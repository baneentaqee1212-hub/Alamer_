package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.ui.components.ActionButtonsRow
import com.example.ui.components.ActiveCallerCard
import com.example.ui.components.CallHistoryList
import com.example.ui.components.CallSimulatorCard
import com.example.ui.components.HeaderBar
import com.example.ui.components.ManualConnectionCard
import com.example.ui.dialogs.DiagnosticsSheet
import com.example.ui.dialogs.NewServerConfirmDialog
import com.example.ui.dialogs.QrScannerSheet
import com.example.ui.dialogs.ServerMismatchDialog
import com.example.ui.dialogs.UntrustedServerDialog
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AlamerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scannerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val diagnosticsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

    val isConnected = uiState.connectionState == ConnectionState.READY

    LaunchedEffect(uiState.lastError) {
        val err = uiState.lastError
        if (!err.isNullOrBlank() && uiState.serverMismatchDetails == null) {
            snackbarHostState.showSnackbar(err)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Slate950,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Header Bar with connection state & POS info
            HeaderBar(
                connectionState = uiState.connectionState,
                trustCredentials = uiState.trustCredentials,
                lastSyncTime = uiState.lastSyncTimestamp
            )

            // Error banner if any
            AnimatedVisibility(
                visible = !uiState.lastError.isNullOrBlank() && uiState.serverMismatchDetails == null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    color = Rose500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.lastError ?: "",
                                fontSize = 12.sp,
                                color = Rose500
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Rose500,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 2. Active Incoming Call Card (Pulsing Amber display)
            AnimatedVisibility(
                visible = uiState.activeCall != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                uiState.activeCall?.let { call ->
                    ActiveCallerCard(
                        call = call,
                        onDismiss = { viewModel.dismissActiveCall() }
                    )
                }
            }

            // 3. Manual URL Connection Card (Mode B / First-Time UX)
            if (!isConnected) {
                ManualConnectionCard(
                    serverUrlInput = uiState.serverUrlInput,
                    onServerUrlChange = { viewModel.updateServerUrlInput(it) },
                    onConnect = { viewModel.connectWithUrl() },
                    onOpenQrScanner = { viewModel.openQrScanner() },
                    isLoading = uiState.isPairingInProgress,
                    connectionState = uiState.connectionState
                )
            }

            // 4. Action Buttons (Pair QR, Diagnostics, Reconnect, Unpair)
            ActionButtonsRow(
                connectionState = uiState.connectionState,
                isPaired = uiState.trustCredentials != null,
                onOpenScanner = { viewModel.openQrScanner() },
                onOpenDiagnostics = { viewModel.openDiagnostics() },
                onReconnect = { viewModel.reconnect() },
                onUnpair = { viewModel.unpair() }
            )

            // 5. Test Call Simulator
            CallSimulatorCard(
                onSimulateCall = { phoneNumber ->
                    viewModel.simulateIncomingCall(phoneNumber)
                }
            )

            // 6. Call History List
            CallHistoryList(
                history = uiState.callHistory
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // QR Scanner Bottom Sheet
        if (uiState.isScanningQr) {
            QrScannerSheet(
                sheetState = scannerSheetState,
                isPairingInProgress = uiState.isPairingInProgress,
                expirySecondsRemaining = uiState.qrExpirySecondsRemaining,
                isQrExpired = uiState.isQrExpired,
                onDismiss = { viewModel.closeQrScanner() },
                onPairWithQr = { qrText ->
                    viewModel.pairWithQr(qrText)
                }
            )
        }

        // Diagnostics Bottom Sheet
        if (uiState.isShowingDiagnostics) {
            DiagnosticsSheet(
                sheetState = diagnosticsSheetState,
                report = uiState.diagnosticsReport,
                isDiscoveryRunning = uiState.isLanDiscoveryRunning,
                onRefresh = { viewModel.refreshDiagnostics() },
                onTriggerDiscovery = { viewModel.triggerLanDiscovery() },
                onDismiss = { viewModel.closeDiagnostics() }
            )
        }

        // Server ID Mismatch Dialog
        uiState.serverMismatchDetails?.let { mismatch ->
            ServerMismatchDialog(
                details = mismatch,
                hasExistingTrust = uiState.trustCredentials != null,
                onConfirmRebind = { viewModel.confirmRebindToMismatchServer() },
                onCancel = { viewModel.cancelMismatch() }
            )
        }

        // Untrusted Server Dialog
        uiState.untrustedServerPrompt?.let { prompt ->
            UntrustedServerDialog(
                prompt = prompt,
                onOpenQrScanner = { viewModel.openQrScanner() },
                onDismiss = { viewModel.dismissUntrustedPrompt() }
            )
        }

        // Verified QR Session Dialog
        uiState.verifiedQrSession?.let { session ->
            if (session.isDifferentFromSavedServer) {
                NewServerConfirmDialog(
                    session = session,
                    onConfirm = { viewModel.confirmPairingVerifiedSession() },
                    onCancel = { viewModel.dismissVerifiedQrSession() }
                )
            }
        }
    }
}
