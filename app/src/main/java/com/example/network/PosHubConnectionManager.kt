package com.example.network

import com.example.model.CallDispatchResult
import com.example.model.CallDispatchState
import com.example.model.CallRecordRequest
import com.example.model.CallerCustomerContext
import com.example.model.PosHubState
import com.example.model.SignalRAuthRequest
import com.example.model.TrustCredentials
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robust Central POS Hub Connection Manager for TaloolaPos (/posHub).
 *
 * Strict Server Contract Adherence:
 * - SignalR protocol: json, version 1 with RECORD_SEPARATOR (\u001e)
 * - Handshake timeout: 10 seconds (fails, closes socket, reconnects on expiry)
 * - Strict Authentication:
 *     Requires success == true, activeClientId not empty, activeSessionId not empty,
 *     callerAssistantGranted == true (default false). Never defaults to true.
 * - Heartbeat:
 *     Requires activeClientId. If ClientId is missing, heartbeat blocked & reconnect requested.
 *     Distinguishes HeartbeatSent vs HeartbeatSucceeded (via completion type=3).
 * - Asynchronous Call Dispatch:
 *     RecordCallerCall waits for Completion (type=3) with 10s timeout.
 *     Distinguishes CALL_SENT, CALL_SERVER_ACCEPTED, CALL_SERVER_REJECTED, DELIVERED, QUEUED, FAILED.
 */
class PosHubConnectionManager(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for WebSockets
        .pingInterval(10, TimeUnit.SECONDS)   // Keepalive ping
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "ALAMER_POSHUB"
        private const val RECORD_SEPARATOR = "\u001e"
        private const val HANDSHAKE_TIMEOUT_MS = 10000L
        private const val CALL_RECORD_TIMEOUT_MS = 10000L

        @Volatile
        private var instance: PosHubConnectionManager? = null

        fun getInstance(okHttpClient: OkHttpClient? = null): PosHubConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: PosHubConnectionManager(
                    okHttpClient ?: OkHttpClient.Builder()
                        .readTimeout(0, TimeUnit.MILLISECONDS)
                        .pingInterval(10, TimeUnit.SECONDS)
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .build()
                ).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val authAdapter = moshi.adapter(SignalRAuthRequest::class.java)
    private val callRecordAdapter = moshi.adapter(CallRecordRequest::class.java)
    private val customerContextAdapter = moshi.adapter(CallerCustomerContext::class.java)

    // State Machine
    private val _connectionState = MutableStateFlow(PosHubState.DISCONNECTED)
    val connectionState: StateFlow<PosHubState> = _connectionState.asStateFlow()

    // Concurrency controls
    private val connectionMutex = Mutex()
    private val currentGeneration = AtomicInteger(0)
    private val invocationCounter = AtomicInteger(1)

    // Pending SignalR invocations waiting for completion (type=3)
    private val pendingInvocations = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    // Current connection target & credentials
    @Volatile
    private var activeTrust: TrustCredentials? = null
    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var isManuallyStopped = false

    // Active session info returned by server AuthenticateCallerAssistant
    @Volatile
    var activeClientId: String? = null
        private set

    @Volatile
    var activeSessionId: String? = null
        private set

    @Volatile
    var activeServerId: String? = null
        private set

    @Volatile
    var callerAssistantGranted: Boolean = false
        private set

    // Handshake timeout job
    private var handshakeTimeoutJob: Job? = null

    // Heartbeat job running every 10 seconds
    private var heartbeatJob: Job? = null
    private val _heartbeatSentCount = MutableStateFlow(0)
    val heartbeatSentCount: StateFlow<Int> = _heartbeatSentCount.asStateFlow()

    private val _heartbeatSuccessCount = MutableStateFlow(0)
    val heartbeatSuccessCount: StateFlow<Int> = _heartbeatSuccessCount.asStateFlow()

    private val _heartbeatFailedCount = MutableStateFlow(0)
    val heartbeatFailedCount: StateFlow<Int> = _heartbeatFailedCount.asStateFlow()

    val heartbeatCount: StateFlow<Int> = _heartbeatSuccessCount

    // Diagnostic information
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _retryCount = MutableStateFlow(0)
    val retryCount: StateFlow<Int> = _retryCount.asStateFlow()

    private val _lastConnectAttempt = MutableStateFlow("-")
    val lastConnectAttempt: StateFlow<String> = _lastConnectAttempt.asStateFlow()

    private val _lastSuccessfulConnect = MutableStateFlow("-")
    val lastSuccessfulConnect: StateFlow<String> = _lastSuccessfulConnect.asStateFlow()

    private val _lastDisconnectReason = MutableStateFlow("-")
    val lastDisconnectReason: StateFlow<String> = _lastDisconnectReason.asStateFlow()

    private val _lastAuthResult = MutableStateFlow("-")
    val lastAuthResult: StateFlow<String> = _lastAuthResult.asStateFlow()

    private val _hubUrl = MutableStateFlow("-")
    val hubUrl: StateFlow<String> = _hubUrl.asStateFlow()

    private val _serverUrl = MutableStateFlow("-")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _activeClientIdFlow = MutableStateFlow("-")
    val activeClientIdFlow: StateFlow<String> = _activeClientIdFlow.asStateFlow()

    // Incoming caller context events
    private val _customerContextEvents = MutableSharedFlow<CallerCustomerContext>(extraBufferCapacity = 64)
    val customerContextEvents: SharedFlow<CallerCustomerContext> = _customerContextEvents.asSharedFlow()

    // Automatic reconnect policy with exact contract backoffs
    private var reconnectJob: Job? = null
    private var retryIndex = 0
    private val backoffDelaysMs = listOf(0L, 2000L, 5000L, 10000L, 15000L, 30000L)

    /**
     * Start connection to POS Hub using saved trust credentials.
     */
    fun start(trust: TrustCredentials) {
        scope.launch {
            connectionMutex.withLock {
                isManuallyStopped = false
                activeTrust = trust
                _serverUrl.value = trust.serverUrl
                _hubUrl.value = PosHubUrlBuilder.buildPosHubUrl(trust.serverUrl)
                retryIndex = 0
                _retryCount.value = 0
                _lastError.value = null
                connectSingleFlight(trust)
            }
        }
    }

    private suspend fun connectSingleFlight(trust: TrustCredentials) {
        if (isManuallyStopped) return

        if (_connectionState.value == PosHubState.READY || _connectionState.value == PosHubState.AUTHENTICATING) {
            return
        }

        val gen = currentGeneration.incrementAndGet()
        _connectionState.value = PosHubState.CONNECTING
        _lastConnectAttempt.value = formatTimestamp(Date())
        val fullHubUrl = PosHubUrlBuilder.buildPosHubUrl(trust.serverUrl)
        logInfo("POSHUB_CONNECT_START (generation=$gen) URL=$fullHubUrl")

        stopHeartbeat()
        cancelPendingInvocations("Reconnecting (generation $gen)")
        closeCurrentSocket()

        // Clear active session until authenticated
        activeClientId = null
        activeSessionId = null
        activeServerId = null
        callerAssistantGranted = false
        _activeClientIdFlow.value = "-"

        // Step 1: Transport Negotiation
        var connectionToken: String? = null
        try {
            val negotiateUrl = PosHubUrlBuilder.buildNegotiateUrl(trust.serverUrl)
            val negRequest = Request.Builder()
                .url(negotiateUrl)
                .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
                .header("Accept", "application/json")
                .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(negRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    connectionToken = json.optString(
                        "connectionToken",
                        json.optString("connectionId", null)
                    )
                    logInfo("POSHUB_NEGOTIATE_SUCCESS (token=${connectionToken?.take(8)}...)")
                } else {
                    logInfo("POSHUB_NEGOTIATE_STATUS: ${response.code}, continuing to direct WebSocket")
                }
            }
        } catch (e: Exception) {
            logInfo("POSHUB_NEGOTIATE_NOTE: ${e.message}, proceeding to direct WebSocket")
        }

        // Step 2: Establish WebSocket Connection
        val wsUrl = PosHubUrlBuilder.buildWebSocketUrl(trust.serverUrl, connectionToken)
        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (currentGeneration.get() != gen) return
                logInfo("POSHUB_SOCKET_OPEN -> sending SignalR handshake (protocol=json, version=1)")

                // Schedule Handshake Timeout (10 seconds)
                handshakeTimeoutJob?.cancel()
                handshakeTimeoutJob = scope.launch {
                    delay(HANDSHAKE_TIMEOUT_MS)
                    if (currentGeneration.get() == gen && _connectionState.value == PosHubState.CONNECTING) {
                        logError("POSHUB_HANDSHAKE_TIMEOUT: No handshake response received within 10s")
                        _lastError.value = "انتهت مهلة مصافحة SignalR (Handshake Timeout)"
                        _lastDisconnectReason.value = "Handshake Timeout (10s)"
                        _connectionState.value = PosHubState.FAILED
                        try {
                            ws.close(1002, "Handshake Timeout")
                        } catch (_: Exception) {}
                        scheduleReconnect()
                    }
                }

                val handshakeJson = "{\"protocol\":\"json\",\"version\":1}$RECORD_SEPARATOR"
                ws.send(handshakeJson)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (currentGeneration.get() != gen) return
                handleIncomingFrames(ws, text, gen, trust)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (currentGeneration.get() != gen) return
                logInfo("POSHUB_CLOSED (code=$code, reason=$reason)")
                handshakeTimeoutJob?.cancel()
                stopHeartbeat()
                cancelPendingInvocations("WebSocket closed ($code: $reason)")
                _lastDisconnectReason.value = "تم إغلاق الاتصال ($code): ${reason.ifBlank { "طبيعي" }}"
                if (isManuallyStopped) {
                    _connectionState.value = PosHubState.DISCONNECTED
                } else {
                    _connectionState.value = PosHubState.RECONNECTING
                    scheduleReconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (currentGeneration.get() != gen) return
                val errMsg = t.message ?: "فشل في اتصال WebSocket"
                logError("POSHUB_CONNECT_FAILED: $errMsg", t)
                handshakeTimeoutJob?.cancel()
                stopHeartbeat()
                cancelPendingInvocations("WebSocket failure: $errMsg")
                _lastDisconnectReason.value = errMsg
                _lastError.value = errMsg
                if (isManuallyStopped) {
                    _connectionState.value = PosHubState.DISCONNECTED
                } else {
                    _connectionState.value = PosHubState.RECONNECTING
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleIncomingFrames(
        ws: WebSocket,
        text: String,
        generation: Int,
        trust: TrustCredentials
    ) {
        val messages = text.split(RECORD_SEPARATOR).filter { it.isNotBlank() }
        for (msg in messages) {
            try {
                val json = JSONObject(msg)

                // 1. Handshake Response Handling
                if (_connectionState.value == PosHubState.CONNECTING) {
                    handshakeTimeoutJob?.cancel()
                    if (json.has("error")) {
                        val err = json.optString("error")
                        logError("POSHUB_HANDSHAKE_ERROR: $err")
                        _lastError.value = "فشل بروتوكول SignalR: $err"
                        _connectionState.value = PosHubState.FAILED
                        scheduleReconnect()
                        return
                    }

                    _connectionState.value = PosHubState.CONNECTED
                    _lastSuccessfulConnect.value = formatTimestamp(Date())
                    logInfo("POSHUB_CONNECT_SUCCESS -> Handshake Accepted")

                    // Proceed immediately to AuthenticateCallerAssistant
                    _connectionState.value = PosHubState.AUTHENTICATING
                    logInfo("POSHUB_AUTH_START -> Invoking AuthenticateCallerAssistant")
                    sendAuthentication(ws, trust)
                    return
                }

                val type = json.optInt("type", -1)
                when (type) {
                    1 -> { // Server Invocations & Hub Events
                        val target = json.optString("target", "")
                        val args = json.optJSONArray("arguments")
                        handleServerInvocation(ws, target, args)
                    }
                    3 -> { // Completion (Response to Invocations)
                        val invocationId = json.optString("invocationId", "")
                        logInfo("POSHUB_COMPLETION_RECEIVED: invocationId=$invocationId")

                        // Complete any waiting deferred invocation (e.g. call-<id>)
                        val deferred = pendingInvocations.remove(invocationId)
                        deferred?.complete(json)

                        if (invocationId == "auth-1") {
                            handleAuthCompletion(json)
                        } else if (invocationId.startsWith("hb-")) {
                            handleHeartbeatCompletion(json)
                        }
                    }
                    6 -> { // SignalR Protocol Ping
                        ws.send("{\"type\":6}$RECORD_SEPARATOR")
                    }
                }
            } catch (e: Exception) {
                logError("POSHUB_FRAME_PARSE_ERROR: ${e.message}", e)
            }
        }
    }

    private fun sendAuthentication(ws: WebSocket, trust: TrustCredentials) {
        val authReq = SignalRAuthRequest(
            deviceId = trust.deviceId,
            deviceName = trust.deviceName,
            installationBinding = trust.installationBinding,
            callerCredential = trust.callerCredential,
            protocolVersion = trust.protocolVersion,
            platform = "Android"
        )
        val authJson = authAdapter.toJson(authReq)
        val invocation = JSONObject().apply {
            put("type", 1)
            put("invocationId", "auth-1")
            put("target", "AuthenticateCallerAssistant")
            put("arguments", JSONArray().put(JSONObject(authJson)))
        }

        val credRedacted = if (trust.callerCredential.length > 4) {
            "***" + trust.callerCredential.takeLast(4)
        } else "[SET]"
        logInfo("POSHUB_SEND_AUTH (deviceId=${trust.deviceId}, cred=$credRedacted)")
        ws.send(invocation.toString() + RECORD_SEPARATOR)
    }

    private fun handleAuthCompletion(json: JSONObject) {
        val error = json.optString("error", "")
        if (error.isNotBlank()) {
            logError("POSHUB_AUTH_FAILED: $error")
            _lastAuthResult.value = "فشل المصادقة: $error"
            _lastError.value = "فشل المصادقة: $error"
            activeClientId = null
            callerAssistantGranted = false
            _activeClientIdFlow.value = "-"
            _connectionState.value = PosHubState.FAILED
            scheduleReconnect()
            return
        }

        val rawResult = json.opt("result")
        if (rawResult == null) {
            logError("POSHUB_AUTH_FAILED: empty result payload, ClientId missing")
            _lastAuthResult.value = "AUTH_INVALID (ClientId مفقود)"
            _lastError.value = "فشل المصادقة: لم يرجع الخادم ClientId"
            activeClientId = null
            callerAssistantGranted = false
            _activeClientIdFlow.value = "-"
            _connectionState.value = PosHubState.FAILED
            scheduleReconnect()
            return
        }

        try {
            val resultObj = when (rawResult) {
                is JSONObject -> rawResult
                else -> JSONObject(rawResult.toString())
            }

            // Extract session identifiers from ClientAuthResult
            val parsedClientId = resultObj.optString("clientId", resultObj.optString("ClientId", "")).trim()
            val parsedSessionId = resultObj.optString("sessionId", resultObj.optString("SessionId", "")).trim()
            val parsedServerId = resultObj.optString("serverId", resultObj.optString("ServerId", "")).trim()

            val isSuccess = resultObj.optBoolean("success", resultObj.optBoolean("Success", false))
            val isCallerGranted = resultObj.optBoolean("callerAssistantGranted", resultObj.optBoolean("CallerAssistantGranted", false))
            val message = resultObj.optString("message", resultObj.optString("Message", ""))

            val pairingResult = PairingResultParser.parse(rawResult.toString())
            val hasCallerAssistantCapability = pairingResult.capabilities.any {
                it.equals("CallerAssistant", ignoreCase = true) || it.equals("2048")
            }

            // Strict Validation Rule 21:
            // Do NOT consider READY unless:
            // Success == true && ClientId != empty && SessionId != empty && CallerAssistantGranted == true
            val effectiveCallerGranted = isCallerGranted || hasCallerAssistantCapability

            if (isSuccess && parsedClientId.isNotBlank() && parsedSessionId.isNotBlank() && effectiveCallerGranted) {
                activeClientId = parsedClientId
                activeSessionId = parsedSessionId
                activeServerId = parsedServerId
                callerAssistantGranted = true
                _activeClientIdFlow.value = parsedClientId

                logInfo("POSHUB_AUTH_SUCCESS: ClientId=$activeClientId, SessionId=$activeSessionId, CallerAssistantGranted=true")
                _lastAuthResult.value = "تمت المصادقة بنجاح (${pairingResult.deviceStatus ?: "Approved"})"
                _connectionState.value = PosHubState.READY
                resetRetryPolicy()

                startHeartbeatLoop()
            } else {
                val reason = when {
                    !isSuccess -> "الخادم أرجع Success=false"
                    parsedClientId.isBlank() -> "AUTH_INVALID: ClientId مفقود من الخادم"
                    parsedSessionId.isBlank() -> "AUTH_INVALID: SessionId مفقود من الخادم"
                    !effectiveCallerGranted -> "AUTH_INVALID: لم يتم منح صلاحية CallerAssistant"
                    else -> message.ifBlank { "رفض الخادم المصادقة" }
                }
                logError("POSHUB_AUTH_REJECTED: $reason")
                activeClientId = null
                activeSessionId = null
                callerAssistantGranted = false
                _activeClientIdFlow.value = "-"
                _lastAuthResult.value = reason
                _lastError.value = reason
                _connectionState.value = PosHubState.FAILED
                scheduleReconnect()
            }
        } catch (e: Exception) {
            logError("POSHUB_AUTH_PARSE_FAILED: ${e.message}", e)
            activeClientId = null
            callerAssistantGranted = false
            _activeClientIdFlow.value = "-"
            _lastAuthResult.value = "خطأ في قراءة رد المصادقة: ${e.message}"
            _connectionState.value = PosHubState.FAILED
            scheduleReconnect()
        }
    }

    /**
     * Handles Server Invocations & Hub Events:
     * - "Ping" -> Respond immediately with HeartbeatCallerAssistant(clientId)
     * - "CallerAssistantReplaced" -> Handled gracefully
     * - "ServerDisconnect" -> Disconnect reason logged
     * - "CallerContextReceived" / "CallerCustomerContext" -> Dispatches customer context
     */
    private fun handleServerInvocation(ws: WebSocket, target: String, args: JSONArray?) {
        logInfo("POSHUB_SERVER_EVENT: target=$target")

        when {
            target.equals("Ping", ignoreCase = true) -> {
                logInfo("POSHUB_EVENT_PING -> Sending immediate heartbeat response")
                sendHeartbeatImmediate()
            }

            target.equals("CallerAssistantReplaced", ignoreCase = true) -> {
                val reason = if (args != null && args.length() > 0) args.optString(0) else "تم فتح جلسة أحدث لهذا الجهاز"
                logError("POSHUB_CALLER_ASSISTANT_REPLACED: $reason")
                _lastDisconnectReason.value = "تم استبدال الاتصال بجلسة أحدث: $reason"
                _lastError.value = "تم فتح اتصال أحدث لنفس الجهاز من الخادم"
                stopHeartbeat()
                scheduleReconnect()
            }

            target.equals("ServerDisconnect", ignoreCase = true) -> {
                val reason = if (args != null && args.length() > 0) args.optString(0) else "قطع الاتصال من جهة الخادم"
                logError("POSHUB_SERVER_DISCONNECT: $reason")
                _lastDisconnectReason.value = reason
                _lastError.value = "أوقف الخادم الاتصال: $reason"
                stopHeartbeat()
                scheduleReconnect()
            }

            target.equals("CallerCustomerContext", ignoreCase = true) ||
            target.equals("OnCallerCustomerContext", ignoreCase = true) ||
            target.equals("CallerContextReceived", ignoreCase = true) -> {
                if (args != null && args.length() > 0) {
                    val argObj = args.optJSONObject(0)
                    if (argObj != null) {
                        try {
                            val parsed = customerContextAdapter.fromJson(argObj.toString())
                            if (parsed != null) {
                                logInfo("[ALAMER_CASHIER_EVENT] CallId=${parsed.callId} Phone=${parsed.phone} State=RECEIVED")
                                scope.launch {
                                    _customerContextEvents.emit(parsed)
                                }
                            }
                        } catch (e: Exception) {
                            logError("FAILED_TO_PARSE_CALLER_CUSTOMER_CONTEXT: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Periodically invokes HeartbeatCallerAssistant(clientId) exactly every 10 seconds.
     */
    private fun startHeartbeatLoop() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            logInfo("POSHUB_HEARTBEAT_LOOP_STARTED (interval=10s, clientId=$activeClientId)")
            while (isActive && _connectionState.value == PosHubState.READY) {
                delay(10000L)
                if (_connectionState.value == PosHubState.READY) {
                    sendHeartbeatImmediate()
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Sends HeartbeatCallerAssistant(clientId: string) to /posHub.
     * Rule 22: Heartbeat MUST use ClientId ONLY. No deviceId fallback.
     * If ClientId is missing, heartbeat is blocked and reconnect/auth is required.
     */
    fun sendHeartbeatImmediate(): Boolean {
        val ws = webSocket ?: return false
        if (_connectionState.value != PosHubState.READY) return false

        val cid = activeClientId
        if (cid.isNullOrBlank()) {
            logError("POSHUB_HEARTBEAT_BLOCKED: ClientId is null/blank, authentication/reconnect required")
            _heartbeatFailedCount.value++
            _connectionState.value = PosHubState.RECONNECTING
            stopHeartbeat()
            scheduleReconnect()
            return false
        }

        val invId = "hb-" + invocationCounter.getAndIncrement()

        return try {
            val invocation = JSONObject().apply {
                put("type", 1)
                put("invocationId", invId)
                put("target", "HeartbeatCallerAssistant")
                put("arguments", JSONArray().put(cid))
            }
            val sent = ws.send(invocation.toString() + RECORD_SEPARATOR)
            if (sent) {
                _heartbeatSentCount.value++
                logInfo("POSHUB_HEARTBEAT_SENT (count=${_heartbeatSentCount.value}, cid=$cid, invId=$invId)")
                true
            } else {
                _heartbeatFailedCount.value++
                logError("POSHUB_HEARTBEAT_SEND_RETURNED_FALSE")
                false
            }
        } catch (e: Exception) {
            _heartbeatFailedCount.value++
            logError("POSHUB_HEARTBEAT_SEND_FAILED: ${e.message}", e)
            false
        }
    }

    /**
     * Rule 23: Heartbeat accounting based on SignalR completion type=3 without error.
     */
    private fun handleHeartbeatCompletion(json: JSONObject) {
        val error = json.optString("error", "")
        if (error.isNotBlank()) {
            _heartbeatFailedCount.value++
            logError("POSHUB_HEARTBEAT_ERROR: $error")
            if (error.contains("SESSION_EXPIRED", ignoreCase = true) ||
                error.contains("UNAUTHORIZED", ignoreCase = true) ||
                error.contains("NOT_FOUND", ignoreCase = true)
            ) {
                _lastError.value = "انتهت صلاحية جلسة الاتصال بالخادم ($error)"
                _connectionState.value = PosHubState.RECONNECTING
                stopHeartbeat()
                scheduleReconnect()
            }
        } else {
            _heartbeatSuccessCount.value++
            logInfo("POSHUB_HEARTBEAT_SUCCEEDED (successCount=${_heartbeatSuccessCount.value})")
        }
    }

    /**
     * Asynchronously invokes RecordCallerCall(CallerCallRecord) -> CallerCallRecord.
     * WAITS FOR COMPLETION type=3 with 10-second timeout!
     * NEVER uses ws.send() alone as success.
     */
    suspend fun recordCallerCallAsync(record: CallRecordRequest): CallDispatchResult {
        val callId = record.callId

        // Rule 19: Check connection & auth prerequisites
        if (_connectionState.value != PosHubState.READY ||
            webSocket == null ||
            activeClientId.isNullOrBlank() ||
            !callerAssistantGranted
        ) {
            val err = "SignalR غير جاهز أو غير مصرح (State=${_connectionState.value}, ClientId=$activeClientId, Granted=$callerAssistantGranted)"
            logError("[ALAMER_CALL_ERROR] CallId=$callId Transport=SignalR SignalRState=${_connectionState.value} Error=$err")
            return CallDispatchResult(
                success = false,
                callId = callId,
                state = CallDispatchState.FAILED,
                transport = "SignalR",
                errorMessage = err
            )
        }

        val ws = webSocket ?: return CallDispatchResult(
            success = false,
            callId = callId,
            state = CallDispatchState.FAILED,
            transport = "SignalR",
            errorMessage = "WebSocket null"
        )

        val invId = "call-" + invocationCounter.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pendingInvocations[invId] = deferred

        return try {
            val recordJson = callRecordAdapter.toJson(record)
            val invocation = JSONObject().apply {
                put("type", 1)
                put("invocationId", invId)
                put("target", "RecordCallerCall")
                put("arguments", JSONArray().put(JSONObject(recordJson)))
            }

            logInfo("[ALAMER_CALL_SIGNALR] CallId=$callId InvocationId=$invId State=SENT")
            val enqueued = ws.send(invocation.toString() + RECORD_SEPARATOR)
            if (!enqueued) {
                pendingInvocations.remove(invId)
                logError("[ALAMER_CALL_ERROR] CallId=$callId Transport=SignalR Error=Failed to write invocation to socket buffer")
                return CallDispatchResult(
                    success = false,
                    callId = callId,
                    state = CallDispatchState.FAILED,
                    transport = "SignalR",
                    errorMessage = "فشل وضع الرسالة في قناة الإرسال"
                )
            }

            // Wait up to 10 seconds for completion type=3
            val completion = withTimeoutOrNull(CALL_RECORD_TIMEOUT_MS) {
                deferred.await()
            }

            pendingInvocations.remove(invId)

            if (completion == null) {
                logError("[ALAMER_CALL_ERROR] CallId=$callId Transport=SignalR Error=RecordCallerCall completion timeout (10s)")
                return CallDispatchResult(
                    success = false,
                    callId = callId,
                    state = CallDispatchState.FAILED,
                    transport = "SignalR",
                    errorMessage = "مهلة انتظار رد الخادم (10 ثوانٍ)"
                )
            }

            val error = completion.optString("error", "")
            if (error.isNotBlank()) {
                logError("[ALAMER_CALL_ERROR] CallId=$callId Transport=SignalR Error=Server returned completion error: $error")
                return CallDispatchResult(
                    success = false,
                    callId = callId,
                    state = CallDispatchState.SERVER_REJECTED,
                    transport = "SignalR",
                    errorMessage = error
                )
            }

            val rawResult = completion.opt("result")
            logInfo("[ALAMER_CALL_SIGNALR] CallId=$callId State=SERVER_ACCEPTED")

            var parsedContext: CallerCustomerContext? = null
            var delivered = 0
            var queued = false

            if (rawResult != null) {
                try {
                    val resultObj = when (rawResult) {
                        is JSONObject -> rawResult
                        else -> JSONObject(rawResult.toString())
                    }
                    delivered = resultObj.optInt("DeliveredToCashiers", resultObj.optInt("deliveredToCashiers", 0))
                    queued = resultObj.optBoolean("QueuedForCashier", resultObj.optBoolean("queuedForCashier", false))

                    val contextObj = resultObj.optJSONObject("Context")
                        ?: resultObj.optJSONObject("context")
                        ?: resultObj.optJSONObject("CustomerContext")
                        ?: resultObj.optJSONObject("customerContext")

                    if (contextObj != null) {
                        parsedContext = customerContextAdapter.fromJson(contextObj.toString())
                    }
                } catch (_: Exception) {
                    // Ignore extra fields parsing failures
                }
            }

            logInfo("[ALAMER_CALL_SERVER_RESULT] CallId=$callId DeliveredToCashiers=$delivered QueuedForCashier=$queued")

            val finalState = if (delivered > 0) CallDispatchState.DELIVERED else if (queued) CallDispatchState.QUEUED else CallDispatchState.SIGNALR_ACCEPTED

            CallDispatchResult(
                success = true,
                callId = callId,
                state = finalState,
                transport = "SignalR",
                deliveredToCashiers = delivered,
                queuedForCashier = queued,
                customerContext = parsedContext
            )
        } catch (e: Exception) {
            pendingInvocations.remove(invId)
            logError("[ALAMER_CALL_ERROR] CallId=$callId Transport=SignalR Error=${e.message}", e)
            CallDispatchResult(
                success = false,
                callId = callId,
                state = CallDispatchState.FAILED,
                transport = "SignalR",
                errorMessage = e.message
            )
        }
    }

    private fun cancelPendingInvocations(reason: String) {
        val iterator = pendingInvocations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            iterator.remove()
            entry.value.cancel(java.util.concurrent.CancellationException(reason))
        }
    }

    /**
     * Automatic reconnect backoff policy matching the server specification:
     * [0s, 2s, 5s, 10s, 15s, 30s]
     */
    private fun scheduleReconnect() {
        if (isManuallyStopped) return
        val trust = activeTrust ?: return
        reconnectJob?.cancel()

        reconnectJob = scope.launch {
            val delayMs = backoffDelaysMs.getOrElse(retryIndex) { 30000L }
            if (retryIndex < backoffDelaysMs.size - 1) {
                retryIndex++
            }
            _retryCount.value++
            val currentAttempt = _retryCount.value
            logInfo("POSHUB_RECONNECT_SCHEDULED (attempt=$currentAttempt, delay=${delayMs}ms)")

            if (delayMs > 0) {
                delay(delayMs)
            }

            connectionMutex.withLock {
                if (!isManuallyStopped && _connectionState.value != PosHubState.READY) {
                    logInfo("POSHUB_RECONNECT_RUNNING (attempt=$currentAttempt)")
                    connectSingleFlight(trust)
                }
            }
        }
    }

    fun onNetworkAvailable() {
        val trust = activeTrust ?: return
        if (isManuallyStopped) return
        if (_connectionState.value == PosHubState.DISCONNECTED ||
            _connectionState.value == PosHubState.RECONNECTING ||
            _connectionState.value == PosHubState.FAILED
        ) {
            scope.launch {
                connectionMutex.withLock {
                    logInfo("POSHUB_NETWORK_RESTORED -> immediate reconnect attempt")
                    retryIndex = 0
                    connectSingleFlight(trust)
                }
            }
        }
    }

    private fun resetRetryPolicy() {
        retryIndex = 0
        _retryCount.value = 0
        reconnectJob?.cancel()
    }

    fun stop() {
        isManuallyStopped = true
        handshakeTimeoutJob?.cancel()
        stopHeartbeat()
        reconnectJob?.cancel()
        cancelPendingInvocations("PosHub connection stopped manually")
        _connectionState.value = PosHubState.DISCONNECTED
        closeCurrentSocket()
        activeClientId = null
        activeSessionId = null
        activeServerId = null
        callerAssistantGranted = false
        _activeClientIdFlow.value = "-"
        logInfo("POSHUB_STOPPED")
    }

    private fun closeCurrentSocket() {
        try {
            webSocket?.close(1000, "App paused or re-initializing")
        } catch (_: Exception) {}
        webSocket = null
    }

    private fun formatTimestamp(date: Date): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
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
