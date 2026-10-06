package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.model.TrustCredentials
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun HeaderBar(
    connectionState: ConnectionState,
    trustCredentials: TrustCredentials?,
    lastSyncTime: String,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState == ConnectionState.READY

    Surface(
        color = Slate900,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (isConnected) Emerald400.copy(alpha = 0.4f) else Slate700, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: App Brand & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Emerald400.copy(alpha = 0.15f) else Cyan400.copy(alpha = 0.15f))
                            .border(1.5.dp, if (isConnected) Emerald400 else Cyan400, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Phone,
                            contentDescription = "ALAMER",
                            tint = if (isConnected) Emerald400 else Cyan400,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isConnected && trustCredentials != null) trustCredentials.serverName else "TALOOLA POS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isConnected) "البدالة متصلة ومستقرة بنسبة 100%" else "ALAMER Caller Assistant",
                            fontSize = 12.sp,
                            color = if (isConnected) Emerald400 else Slate400,
                            fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                StatusPill(connectionState = connectionState)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Info Row: Server URL & Status indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate800)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Wi-Fi LAN",
                        tint = if (isConnected) Emerald400 else Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = trustCredentials?.let { "الخادم: ${it.host}:${it.port}" } ?: "الخادم: غير مقترن",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isConnected) {
                        Surface(
                            color = Emerald400.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Emerald400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = Slate400,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (lastSyncTime != "-") lastSyncTime else "READY",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }
            }
        }
    }
}

private data class StatusPillStyle(
    val bgColor: Color,
    val textColor: Color,
    val dotColor: Color,
    val label: String
)

@Composable
fun StatusPill(connectionState: ConnectionState) {
    val style = when (connectionState) {
        ConnectionState.READY -> StatusPillStyle(Emerald400.copy(alpha = 0.15f), Emerald400, Emerald400, "متصل ومستقر")
        ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING, ConnectionState.PAIRING ->
            StatusPillStyle(Amber400.copy(alpha = 0.15f), Amber400, Amber400, connectionState.arabicLabel)
        ConnectionState.NEEDS_PAIRING, ConnectionState.UNINITIALIZED ->
            StatusPillStyle(Slate700.copy(alpha = 0.6f), Slate400, Slate400, "بانتظار الاقتران")
        else -> StatusPillStyle(Rose500.copy(alpha = 0.15f), Rose500, Rose500, connectionState.arabicLabel)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = style.bgColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (connectionState in listOf(ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING, ConnectionState.PAIRING))
                            style.dotColor.copy(alpha = alphaAnim)
                        else style.dotColor
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = style.label,
                color = style.textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
