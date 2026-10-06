package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AlamerRepository
import com.example.model.CallHistoryItem
import com.example.model.ConnectionState
import com.example.model.DiagnosticsReport
import com.example.model.QrValidationResult
import com.example.model.ServerMismatchDetails
import com.example.model.TrustCredentials
import com.example.model.UntrustedServerPrompt
import com.example.model.VerifiedQrSession
import com.example.network.QrParserAndValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlamerUiState(
    val connectionState: ConnectionState = ConnectionState.UNINITIALIZED,
    val trustCredentials: TrustCredentials? = null,
    val activeCall: CallHistoryItem? = null,
    val callHistory: List<CallHistoryItem> = emptyList(),
    val lastError: String? = null,
    val diagnosticsReport: DiagnosticsReport = DiagnosticsReport(),
    val lastSyncTimestamp: String = "-",
    val isPairingInProgress: Boolean = false,
    val isScanningQr: Boolean = false,
    val isShowingDiagnostics: Boolean = false,
    val qrValidationResult: QrValidationResult? = null,
    val isLanDiscoveryRunning: Boolean = false,
    val serverUrlInput: String = "http://192.168.68.104:5000",
    val serverMismatchDetails: ServerMismatchDetails? = null,
    val untrustedServerPrompt: UntrustedServerPrompt? = null,
    val verifiedQrSession: VerifiedQrSession? = null,
    val qrExpirySecondsRemaining: Long = 0,
    val isQrExpired: Boolean = false
)

class AlamerViewModel(
    private val repository: AlamerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlamerUiState())
    val uiState: StateFlow<AlamerUiState> = _uiState.asStateFlow()

    private var qrCountdownJob: Job? = null

    init {
        // Initialize serverUrlInput from saved trust if exists
        val saved = repository.secureStorage.loadTrust()
        if (saved != null) {
            _uiState.value = _uiState.value.copy(serverUrlInput = saved.serverUrl)
        }

        // Collect repository states
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    connectionState = state,
                    isPairingInProgress = state == ConnectionState.PAIRING
                )
            }
        }

        viewModelScope.launch {
            repository.trustCredentials.collect { trust ->
                _uiState.value = _uiState.value.copy(
                    trustCredentials = trust,
                    serverUrlInput = trust?.serverUrl ?: _uiState.value.serverUrlInput
                )
            }
        }

        viewModelScope.launch {
            repository.callManager.activeCall.collect { call ->
                _uiState.value = _uiState.value.copy(activeCall = call)
            }
        }

        viewModelScope.launch {
            repository.callManager.callHistory.collect { history ->
                _uiState.value = _uiState.value.copy(callHistory = history)
            }
        }

        viewModelScope.launch {
            repository.lastError.collect { error ->
                _uiState.value = _uiState.value.copy(lastError = error)
            }
        }

        viewModelScope.launch {
            repository.diagnosticsReport.collect { report ->
                _uiState.value = _uiState.value.copy(diagnosticsReport = report)
            }
        }

        viewModelScope.launch {
            repository.lastSyncTimestamp.collect { sync ->
                _uiState.value = _uiState.value.copy(lastSyncTimestamp = sync)
            }
        }

        viewModelScope.launch {
            repository.serverMismatchDetails.collect { mismatch ->
                _uiState.value = _uiState.value.copy(serverMismatchDetails = mismatch)
            }
        }

        viewModelScope.launch {
            repository.untrustedServerPrompt.collect { prompt ->
                _uiState.value = _uiState.value.copy(untrustedServerPrompt = prompt)
            }
        }

        viewModelScope.launch {
            repository.verifiedQrSession.collect { session ->
                _uiState.value = _uiState.value.copy(verifiedQrSession = session)
            }
        }
    }

    fun updateServerUrlInput(newUrl: String) {
        _uiState.value = _uiState.value.copy(serverUrlInput = newUrl)
    }

    fun connectWithUrl(rawUrl: String = _uiState.value.serverUrlInput) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPairingInProgress = true)
            repository.connectByUrl(rawUrl)
            _uiState.value = _uiState.value.copy(isPairingInProgress = false)
        }
    }

    fun validateQrText(qrText: String): QrValidationResult {
        val result = QrParserAndValidator.validate(qrText)
        _uiState.value = _uiState.value.copy(qrValidationResult = result)
        if (result.isValid && result.data != null) {
            startQrExpiryCountdown(result.data.expiryEpochSeconds)
        } else {
            qrCountdownJob?.cancel()
            _uiState.value = _uiState.value.copy(qrExpirySecondsRemaining = 0, isQrExpired = false)
        }
        return result
    }

    private fun startQrExpiryCountdown(expiryEpochSeconds: Long) {
        qrCountdownJob?.cancel()
        qrCountdownJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis() / 1000
                val diff = expiryEpochSeconds - now
                if (diff <= 0) {
                    _uiState.value = _uiState.value.copy(
                        qrExpirySecondsRemaining = 0,
                        isQrExpired = true
                    )
                    break
                } else {
                    _uiState.value = _uiState.value.copy(
                        qrExpirySecondsRemaining = diff,
                        isQrExpired = false
                    )
                }
                delay(1000)
            }
        }
    }

    fun pairWithQr(qrText: String) {
        val validation = validateQrText(qrText)
        if (!validation.isValid || validation.data == null) {
            _uiState.value = _uiState.value.copy(
                lastError = validation.errors.firstOrNull() ?: "رمز QR غير صالح"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPairingInProgress = true,
                isScanningQr = false
            )
            val verifyRes = repository.verifyQrSession(validation.data)
            _uiState.value = _uiState.value.copy(isPairingInProgress = false)
            if (verifyRes.isSuccess) {
                val session = verifyRes.getOrThrow()
                if (!session.isDifferentFromSavedServer) {
                    _uiState.value = _uiState.value.copy(isPairingInProgress = true)
                    repository.executeVerifiedPairing(session)
                    _uiState.value = _uiState.value.copy(isPairingInProgress = false)
                }
            } else {
                val ex = verifyRes.exceptionOrNull()
                if (repository.serverMismatchDetails.value == null) {
                    _uiState.value = _uiState.value.copy(lastError = ex?.message)
                }
            }
        }
    }

    fun confirmPairingVerifiedSession() {
        val session = _uiState.value.verifiedQrSession ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPairingInProgress = true)
            repository.executeVerifiedPairing(session)
            _uiState.value = _uiState.value.copy(isPairingInProgress = false)
        }
    }

    fun dismissVerifiedQrSession() {
        repository.clearVerifiedQrSession()
    }

    fun confirmRebindToMismatchServer() {
        val mismatch = _uiState.value.serverMismatchDetails ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPairingInProgress = true)
            repository.confirmRebindToMismatchServer(mismatch)
            _uiState.value = _uiState.value.copy(isPairingInProgress = false)
        }
    }

    fun cancelMismatch() {
        repository.clearMismatchDetails()
        _uiState.value = _uiState.value.copy(lastError = null)
    }

    fun dismissUntrustedPrompt() {
        repository.clearUntrustedServerPrompt()
    }

    fun reconnect() {
        val trust = repository.trustCredentials.value ?: repository.secureStorage.loadTrust()
        if (trust != null) {
            repository.startAutoReconnect(trust)
        } else {
            connectWithUrl(_uiState.value.serverUrlInput)
        }
    }

    fun unpair() {
        repository.unpair()
    }

    fun simulateIncomingCall(phoneNumber: String) {
        repository.callManager.onIncomingCallRinging(phoneNumber)
    }

    fun dismissActiveCall() {
        repository.callManager.dismissActiveCall()
    }

    fun openQrScanner() {
        _uiState.value = _uiState.value.copy(isScanningQr = true)
    }

    fun closeQrScanner() {
        qrCountdownJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanningQr = false, qrValidationResult = null)
    }

    fun openDiagnostics() {
        _uiState.value = _uiState.value.copy(isShowingDiagnostics = true)
        refreshDiagnostics()
    }

    fun closeDiagnostics() {
        _uiState.value = _uiState.value.copy(isShowingDiagnostics = false)
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            repository.runDiagnostics()
        }
    }

    fun triggerLanDiscovery() {
        val trust = repository.trustCredentials.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLanDiscoveryRunning = true)
            val discovered = repository.udpDiscoveryClient.discoverServer(trust.serverId, 2500)
            _uiState.value = _uiState.value.copy(isLanDiscoveryRunning = false)
            if (discovered != null) {
                repository.secureStorage.updateEndpoint(discovered.host, discovered.port, discovered.serverUrl)
                reconnect()
            } else {
                _uiState.value = _uiState.value.copy(
                    lastError = "لم يتم العثور على خادم Taloola على الشبكة المحلية"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(lastError = null)
    }

    class Factory(private val repository: AlamerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlamerViewModel(repository) as T
        }
    }
}
