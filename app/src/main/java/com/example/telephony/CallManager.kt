package com.example.telephony

import com.example.model.CallDispatchResult
import com.example.model.CallDispatchState
import com.example.model.CallHistoryItem
import com.example.model.CallRecordRequest
import com.example.model.CallerCustomerContext
import com.example.network.SignalRClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Manages call lifecycle, stable CallId, idempotency, customer context enrichment,
 * and reliable SignalR with HTTP fallback dispatching.
 *
 * Sequence:
 *   Incoming Call
 *        ↓
 *   CallManager creates CallId
 *        ↓
 *   SignalR /posHub RecordCallerCall (awaits type=3 completion with 10s timeout)
 *        ↓
 *   If SignalR accepted:
 *       markCallDispatched (State = DELIVERED or QUEUED or SIGNALR_ACCEPTED)
 *       attach returned customerContext if available
 *       stop
 *        ↓
 *   If SignalR fails / timeout / rejected:
 *       HTTP fallback: POST /api/caller-assistant/call
 *       If HTTP success:
 *           markCallDispatched (State = DELIVERED or QUEUED)
 *           attach returned customerContext
 *       Else:
 *           queue locally with exact same CallId (no duplicate ID)
 */
class CallManager(
    private val signalRClient: SignalRClient
) {
    companion object {
        private const val TAG = "ALAMER_CALL"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dispatchMutex = Mutex()

    // Active incoming call state
    private val _activeCall = MutableStateFlow<CallHistoryItem?>(null)
    val activeCall: StateFlow<CallHistoryItem?> = _activeCall.asStateFlow()

    // Call history list (most recent first)
    private val _callHistory = MutableStateFlow<List<CallHistoryItem>>(emptyList())
    val callHistory: StateFlow<List<CallHistoryItem>> = _callHistory.asStateFlow()

    // Processed CallIds set to guarantee IDEMPOTENCY
    private val processedCallIds = mutableSetOf<String>()

    // Offline buffer for calls queued when transport is temporarily unavailable
    private val offlineQueue = mutableListOf<CallRecordRequest>()

    // Current active call ID
    @Volatile
    private var currentActiveCallId: String? = null

    // Fallback HTTP Dispatcher: suspend function returning CallDispatchResult
    var httpFallbackDispatcher: (suspend (CallRecordRequest) -> CallDispatchResult)? = null

    init {
        // Listen to CallerContextReceived from SignalR
        scope.launch {
            signalRClient.customerContextEvents.collect { context ->
                handleIncomingCustomerContext(context)
            }
        }
    }

    /**
     * Called when phone rings or when manual simulation is triggered.
     * Enforces single stable CallId and idempotency.
     */
    fun onIncomingCallRinging(rawPhoneNumber: String): String {
        val normalized = normalizePhoneNumber(rawPhoneNumber)

        // If this is an existing active call for the same number ringing repeatedly, keep stable CallId
        val existingActive = _activeCall.value
        if (existingActive != null && existingActive.normalizedPhone == normalized) {
            logInfo("[ALAMER_CALL] CallId=${existingActive.callId} Phone=${existingActive.phone} State=ALREADY_ACTIVE (idempotent)")
            return existingActive.callId
        }

        val newCallId = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()
        val utcIsoTime = formatIsoUtc(Date(nowMs))

        val callItem = CallHistoryItem(
            callId = newCallId,
            phone = rawPhoneNumber.ifBlank { "رقم مجهول" },
            normalizedPhone = normalized,
            direction = "incoming",
            startedAtUtc = utcIsoTime,
            timestampMs = nowMs,
            isDispatchedToTaloola = false,
            dispatchState = CallDispatchState.CREATED,
            transport = null,
            deliveredToCashiers = 0,
            queuedForCashier = false,
            customerContext = null,
            error = null
        )

        currentActiveCallId = newCallId
        processedCallIds.add(newCallId)
        _activeCall.value = callItem
        _callHistory.value = listOf(callItem) + _callHistory.value.take(49)

        logInfo("[ALAMER_CALL] CallId=$newCallId Phone=${callItem.phone} State=CREATED")

        val request = CallRecordRequest(
            callId = newCallId,
            phone = callItem.phone,
            normalizedPhone = normalized,
            direction = "incoming",
            startedAtUtc = utcIsoTime
        )

        scope.launch {
            dispatchCallRecord(request)
        }

        return newCallId
    }

    /**
     * Dispatches call record with strict order:
     * 1. Attempt SignalR RecordCallerCall and await completion.
     * 2. If SignalR succeeds, mark dispatched and stop. (Do not send HTTP to avoid duplicate!)
     * 3. If SignalR fails/times out, execute HTTP fallback with the exact same CallId.
     * 4. If HTTP succeeds, mark dispatched.
     * 5. If both fail, enqueue locally for retry.
     */
    suspend fun dispatchCallRecord(request: CallRecordRequest): CallDispatchResult {
        return dispatchMutex.withLock {
            val callId = request.callId

            // Step 1: Attempt SignalR
            logInfo("[ALAMER_CALL_SIGNALR] CallId=$callId State=SENT")
            val signalRResult = signalRClient.recordCallerCallAsync(request)

            if (signalRResult.success) {
                logInfo("[ALAMER_CALL_SIGNALR] CallId=$callId State=SERVER_ACCEPTED")
                logInfo("[ALAMER_CALL_SERVER_RESULT] CallId=$callId DeliveredToCashiers=${signalRResult.deliveredToCashiers} QueuedForCashier=${signalRResult.queuedForCashier}")
                markCallDispatched(signalRResult)
                return@withLock signalRResult
            }

            logError("[ALAMER_CALL_ERROR] CallId=$callId Transport=SignalR SignalRState=${signalRClient.hubManager.connectionState.value} Error=${signalRResult.errorMessage}")

            // Step 2: HTTP Fallback
            val httpDispatcher = httpFallbackDispatcher
            if (httpDispatcher != null) {
                logInfo("[ALAMER_CALL] CallId=$callId Transport=HTTP State=HTTP_FALLBACK_SENT")
                val httpResult = try {
                    httpDispatcher.invoke(request)
                } catch (e: Exception) {
                    logError("[ALAMER_CALL_ERROR] CallId=$callId Transport=HTTP Error=${e.message}", e)
                    CallDispatchResult(
                        success = false,
                        callId = callId,
                        state = CallDispatchState.FAILED,
                        transport = "HTTP",
                        errorMessage = e.message
                    )
                }

                if (httpResult.success) {
                    logInfo("[ALAMER_CALL_SERVER_RESULT] CallId=$callId DeliveredToCashiers=${httpResult.deliveredToCashiers} QueuedForCashier=${httpResult.queuedForCashier}")
                    markCallDispatched(httpResult)
                    return@withLock httpResult
                }
            }

            // Step 3: Local queue for safe retry without altering CallId
            logInfo("[ALAMER_CALL] CallId=$callId State=QUEUED_LOCALLY")
            synchronized(offlineQueue) {
                if (offlineQueue.none { it.callId == callId }) {
                    offlineQueue.add(request)
                }
            }

            updateCallState(
                callId = callId,
                state = CallDispatchState.FAILED,
                transport = "SignalR+HTTP",
                isDispatched = false,
                delivered = 0,
                queued = false,
                context = null,
                error = signalRResult.errorMessage ?: "فشل الإرسال عبر SignalR وHTTP"
            )

            signalRResult
        }
    }

    /**
     * Called when SignalR re-establishes connection. Flushes queued calls safely.
     */
    fun onSignalRConnected() {
        scope.launch {
            flushOfflineQueue()
        }
    }

    suspend fun flushOfflineQueue() {
        val toSend = synchronized(offlineQueue) {
            val list = ArrayList(offlineQueue)
            offlineQueue.clear()
            list
        }

        for (req in toSend) {
            val result = dispatchCallRecord(req)
            if (!result.success) {
                synchronized(offlineQueue) {
                    if (offlineQueue.none { it.callId == req.callId }) {
                        offlineQueue.add(req)
                    }
                }
            }
        }
    }

    private fun markCallDispatched(result: CallDispatchResult) {
        updateCallState(
            callId = result.callId,
            state = result.state,
            transport = result.transport,
            isDispatched = true,
            delivered = result.deliveredToCashiers,
            queued = result.queuedForCashier,
            context = result.customerContext,
            error = null
        )
    }

    private fun updateCallState(
        callId: String,
        state: CallDispatchState,
        transport: String?,
        isDispatched: Boolean,
        delivered: Int,
        queued: Boolean,
        context: CallerCustomerContext?,
        error: String?
    ) {
        val current = _activeCall.value
        if (current?.callId == callId) {
            _activeCall.value = current.copy(
                isDispatchedToTaloola = isDispatched,
                dispatchState = state,
                transport = transport ?: current.transport,
                deliveredToCashiers = delivered,
                queuedForCashier = queued,
                customerContext = context ?: current.customerContext,
                error = error
            )
        }
        _callHistory.value = _callHistory.value.map { item ->
            if (item.callId == callId) {
                item.copy(
                    isDispatchedToTaloola = isDispatched,
                    dispatchState = state,
                    transport = transport ?: item.transport,
                    deliveredToCashiers = delivered,
                    queuedForCashier = queued,
                    customerContext = context ?: item.customerContext,
                    error = error
                )
            } else item
        }
    }

    /**
     * Rule 16: Associate CallerCustomerContext with the call using CallId first, NormalizedPhone second.
     */
    private fun handleIncomingCustomerContext(context: CallerCustomerContext) {
        val targetCallId = context.callId ?: currentActiveCallId

        logInfo("[ALAMER_CASHIER_EVENT] CallId=${context.callId} Phone=${context.phone} State=RECEIVED")

        val active = _activeCall.value
        if (active != null) {
            val matchesCallId = targetCallId != null && active.callId == targetCallId
            val matchesPhone = active.normalizedPhone == context.normalizedPhone
            if (matchesCallId || matchesPhone) {
                _activeCall.value = active.copy(customerContext = context)
            }
        }

        _callHistory.value = _callHistory.value.map { item ->
            val matchesCallId = targetCallId != null && item.callId == targetCallId
            val matchesPhone = item.normalizedPhone == context.normalizedPhone && item.customerContext == null
            if (matchesCallId || matchesPhone) {
                item.copy(customerContext = context)
            } else {
                item
            }
        }
    }

    fun dismissActiveCall() {
        _activeCall.value = null
        currentActiveCallId = null
    }

    fun clearHistory() {
        _callHistory.value = emptyList()
        _activeCall.value = null
        currentActiveCallId = null
        processedCallIds.clear()
        synchronized(offlineQueue) {
            offlineQueue.clear()
        }
    }

    fun normalizePhoneNumber(phone: String): String {
        val trimmed = phone.trim()
        val digitsOnly = trimmed.filter { it.isDigit() || it == '+' }
        return if (digitsOnly.isNotBlank()) digitsOnly else phone.filter { it.isDigit() }
    }

    private fun formatIsoUtc(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    private fun logInfo(msg: String) {
        try {
            android.util.Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("$TAG: $msg")
        }
    }

    private fun logError(msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(TAG, msg, tr)
        } catch (_: Throwable) {
            System.err.println("$TAG: $msg")
            tr?.printStackTrace()
        }
    }
}
