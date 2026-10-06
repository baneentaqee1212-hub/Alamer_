package com.example.ui.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiagnosticsReport
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    sheetState: SheetState,
    report: DiagnosticsReport,
    isDiscoveryRunning: Boolean,
    onRefresh: () -> Unit,
    onTriggerDiscovery: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate900,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "تشخيص اتصال Taloola POS",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostic Items Grid/List
            Surface(
                color = Slate800,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Wi-Fi Status
                    DiagItemRow(
                        icon = Icons.Default.Wifi,
                        title = "شبكة Wi-Fi المحلية:",
                        value = "${report.wifiSsid} (${report.localIp})",
                        isSuccess = report.wifiConnected
                    )

                    // Server & Hub URL
                    DiagItemRow(
                        icon = Icons.Default.Router,
                        title = "عنوان الخادم الأساسي:",
                        value = report.serverUrl.ifBlank { "${report.savedHost}:${report.savedPort}" },
                        isSuccess = report.serverUrl != "-"
                    )

                    DiagItemRow(
                        icon = Icons.Default.Router,
                        title = "مسار POS Hub (SignalR):",
                        value = report.hubUrl,
                        isSuccess = report.hubUrl != "-"
                    )

                    // HTTP Reachability
                    DiagItemRow(
                        icon = if (report.serverReachability) Icons.Default.CheckCircle else Icons.Default.Error,
                        title = "فحص المنفذ 5000 (/health):",
                        value = if (report.serverReachability) "متاح (HTTP 200 OK)" else "غير متاح",
                        isSuccess = report.serverReachability
                    )

                    // ServerInfo check
                    DiagItemRow(
                        icon = if (report.serverIdMatch) Icons.Default.CheckCircle else Icons.Default.Error,
                        title = "تطابق معرف الخادم (ServerId):",
                        value = if (report.serverIdMatch) "متطابق 100%" else "مختلف أو غير مفحوص",
                        isSuccess = report.serverIdMatch
                    )

                    // Credential Status
                    DiagItemRow(
                        icon = Icons.Default.Security,
                        title = "بيانات الاعتماد المحفوظة:",
                        value = report.credentialStatus,
                        isSuccess = report.credentialStatus.contains("مشفرة")
                    )

                    // SignalR Status
                    val isSignalROnline = report.signalRStatus.contains("READY") || report.signalRStatus.contains("متصل")
                    DiagItemRow(
                        icon = if (isSignalROnline) Icons.Default.CheckCircle else Icons.Default.Refresh,
                        title = "حالة اتصال SignalR (/posHub):",
                        value = report.signalRStatus,
                        isSuccess = isSignalROnline
                    )

                    // ClientId
                    DiagItemRow(
                        icon = Icons.Default.Security,
                        title = "معرف جلسة العميل (ClientId):",
                        value = report.activeClientId,
                        isSuccess = report.activeClientId != "-"
                    )

                    // Heartbeat counter (Section 2 - 10s rule)
                    DiagItemRow(
                        icon = Icons.Default.Favorite,
                        title = "نبضات الاتصال (كل 10 ثوانٍ):",
                        value = "${report.heartbeatCount} نبضة مُرسلة بنجاح",
                        isSuccess = report.heartbeatCount > 0
                    )

                    // Auth Result
                    DiagItemRow(
                        icon = if (report.lastAuthResult.contains("بنجاح")) Icons.Default.CheckCircle else Icons.Default.Security,
                        title = "نتيجة AuthenticateCallerAssistant:",
                        value = report.lastAuthResult,
                        isSuccess = report.lastAuthResult.contains("بنجاح")
                    )

                    // Connect attempts & last success
                    DiagItemRow(
                        icon = Icons.Default.Refresh,
                        title = "آخر محاولة / آخر نجاح:",
                        value = "${report.lastConnectAttempt} / ${report.lastSuccessfulConnect}",
                        isSuccess = report.lastSuccessfulConnect != "-"
                    )

                    // Disconnect Reason if any
                    if (report.lastDisconnectReason != "-") {
                        DiagItemRow(
                            icon = Icons.Default.Error,
                            title = "سبب آخر انقطاع:",
                            value = report.lastDisconnectReason,
                            isSuccess = false
                        )
                    }

                    // Retry Count
                    DiagItemRow(
                        icon = Icons.Default.Refresh,
                        title = "محاولات إعادة الاتصال:",
                        value = "${report.retryCount} محاولة",
                        isSuccess = report.retryCount == 0 || isSignalROnline
                    )
                }
            }

            // Error display if present
            if (!report.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Rose500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Rose500,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = report.lastError,
                            fontSize = 12.sp,
                            color = Rose500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Refresh & DHCP Discovery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحديث الفحص", fontSize = 12.sp)
                }

                Button(
                    onClick = onTriggerDiscovery,
                    enabled = !isDiscoveryRunning,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan500,
                        contentColor = Slate950
                    ),
                    modifier = Modifier.weight(1.2f)
                ) {
                    if (isDiscoveryRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Slate950,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جارٍ البحث...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.FindInPage,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("كشف الخادم (UDP)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    isSuccess: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSuccess) Emerald400 else Slate400,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = Slate400
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSuccess) Color.White else Amber400
        )
    }
}
