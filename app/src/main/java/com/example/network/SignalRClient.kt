package com.example.network

import com.example.model.CallDispatchResult
import com.example.model.CallRecordRequest
import com.example.model.CallerCustomerContext
import com.example.model.PosHubState
import com.example.model.SignalRAuthRequest
import com.example.model.TrustCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SignalRConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    AUTHENTICATING,
    CONNECTED,
    FAILED
}

/**
 * Compatibility wrapper delegating directly to PosHubConnectionManager.
 * Ensures single connection manager without duplicate sockets or parallel attempts.
 */
class SignalRClient(
    val hubManager: PosHubConnectionManager = PosHubConnectionManager.getInstance()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _connectionState = MutableStateFlow(SignalRConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SignalRConnectionState> = _connectionState.asStateFlow()

    val customerContextEvents: SharedFlow<CallerCustomerContext> = hubManager.customerContextEvents
    val lastError: StateFlow<String?> = hubManager.lastError
    val retryCount: StateFlow<Int> = hubManager.retryCount

    init {
        scope.launch {
            hubManager.connectionState.collect { posState ->
                _connectionState.value = when (posState) {
                    PosHubState.DISCONNECTED -> SignalRConnectionState.DISCONNECTED
                    PosHubState.CONNECTING -> SignalRConnectionState.CONNECTING
                    PosHubState.CONNECTED -> SignalRConnectionState.HANDSHAKING
                    PosHubState.AUTHENTICATING -> SignalRConnectionState.AUTHENTICATING
                    PosHubState.READY -> SignalRConnectionState.CONNECTED
                    PosHubState.RECONNECTING -> SignalRConnectionState.CONNECTING
                    PosHubState.STOPPING -> SignalRConnectionState.DISCONNECTED
                    PosHubState.FAILED -> SignalRConnectionState.FAILED
                }
            }
        }
    }

    fun start(
        host: String,
        port: Int,
        tls: Boolean,
        hubPath: String = "/posHub",
        authRequest: SignalRAuthRequest
    ) {
        val scheme = if (tls) "https" else "http"
        val serverUrl = "$scheme://$host:$port"
        val trust = TrustCredentials(
            serverId = "",
            serverName = "Alamer",
            serverUrl = serverUrl,
            host = host,
            port = port,
            tlsRequired = tls,
            protocolVersion = authRequest.protocolVersion,
            deviceId = authRequest.deviceId,
            deviceName = authRequest.deviceName,
            installationBinding = authRequest.installationBinding,
            callerCredential = authRequest.callerCredential,
            hubPath = hubPath
        )
        hubManager.start(trust)
    }

    fun start(trust: TrustCredentials) {
        hubManager.start(trust)
    }

    fun stop() {
        hubManager.stop()
    }

    /**
     * Asynchronously records caller call awaiting completion type=3 with 10s timeout.
     */
    suspend fun recordCallerCallAsync(record: CallRecordRequest): CallDispatchResult {
        return hubManager.recordCallerCallAsync(record)
    }
}
