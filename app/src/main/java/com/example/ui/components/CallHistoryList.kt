package com.example.ui.components

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
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CallHistoryItem
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallHistoryList(
    history: List<CallHistoryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "سجل المكالمات الواردة",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = "${history.size} مكالمة",
                fontSize = 12.sp,
                color = Slate400
            )
        }

        if (history.isEmpty()) {
            EmptyHistoryCard()
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                history.forEach { item ->
                    CallHistoryRow(item = item)
                }
            }
        }
    }
}

@Composable
fun CallHistoryRow(item: CallHistoryItem) {
    val timeFormatted = try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(item.timestampMs))
    } catch (e: Exception) {
        "-"
    }

    val context = item.customerContext

    Surface(
        color = Slate900,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(14.dp))
            .testTag("call_history_item_${item.callId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CallReceived,
                        contentDescription = "Incoming",
                        tint = Cyan400,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.phone,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (!context?.name.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = context.name ?: "",
                                fontSize = 12.sp,
                                color = Emerald400,
                                fontWeight = FontWeight.Medium
                            )
                            if (!context.area.isNullOrBlank()) {
                                Text(
                                    text = " • ${context.area}",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "تم التوجيه إلى الكاشير",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeFormatted,
                    fontSize = 12.sp,
                    color = Slate400
                )
                Spacer(modifier = Modifier.height(4.dp))
                val statusLabel = when (item.dispatchState) {
                    com.example.model.CallDispatchState.DELIVERED -> "مُسلّم للكاشير (${item.deliveredToCashiers})"
                    com.example.model.CallDispatchState.QUEUED -> "بالانتظار (Queued)"
                    com.example.model.CallDispatchState.SIGNALR_ACCEPTED -> "مقبول بالخادم"
                    com.example.model.CallDispatchState.HTTP_FALLBACK_SENT -> "مُرسل (HTTP)"
                    com.example.model.CallDispatchState.SERVER_REJECTED -> "مرفوض"
                    com.example.model.CallDispatchState.FAILED -> "فشل الإرسال"
                    else -> if (item.isDispatchedToTaloola) "مُرسل للـ POS" else "قيد الإرسال"
                }
                val statusDotColor = when (item.dispatchState) {
                    com.example.model.CallDispatchState.DELIVERED -> Emerald400
                    com.example.model.CallDispatchState.QUEUED,
                    com.example.model.CallDispatchState.SIGNALR_ACCEPTED -> Cyan400
                    com.example.model.CallDispatchState.SERVER_REJECTED,
                    com.example.model.CallDispatchState.FAILED -> com.example.ui.theme.Rose500
                    else -> Amber400
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        color = statusDotColor
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryCard() {
    Surface(
        color = Slate900,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PhoneCallback,
                contentDescription = null,
                tint = Slate700,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "لا توجد مكالمات بعد",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Slate400
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "المكالمات الواردة للهاتف سترسل فوراً إلى خادم Taloola POS",
                fontSize = 12.sp,
                color = Slate400.copy(alpha = 0.7f)
            )
        }
    }
}
