package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.model.CallHistoryItem
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun ActiveCallerCard(
    call: CallHistoryItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_caller_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Slate900
        ),
        border = BorderStroke(2.dp, Amber400.copy(alpha = borderAlpha))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Live Call Indicator & Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Amber400.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Amber400)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "مكالمة واردة الآن...",
                            color = Amber400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phone Number & Handset Icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Amber400.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "مكالمة",
                        tint = Amber400,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = call.phone,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = when (call.dispatchState) {
                        com.example.model.CallDispatchState.CREATED -> "جارٍ إنشاء الطلب..."
                        com.example.model.CallDispatchState.SIGNALR_SENT -> "جارٍ الإرسال عبر SignalR (/posHub)..."
                        com.example.model.CallDispatchState.SIGNALR_ACCEPTED -> "تم قبول المكالمة من خادم Taloola"
                        com.example.model.CallDispatchState.DELIVERED -> "تم تسليم بيانات المتصل إلى الكاشير بنجاح (${call.deliveredToCashiers})"
                        com.example.model.CallDispatchState.QUEUED -> "في انتظار دخول الكاشير (Queued)"
                        com.example.model.CallDispatchState.HTTP_FALLBACK_SENT -> "جارٍ الإرسال عبر مسار HTTP الاحتياطي..."
                        com.example.model.CallDispatchState.SERVER_REJECTED -> "رفض الخادم المكالمة: ${call.error ?: ""}"
                        com.example.model.CallDispatchState.FAILED -> "فشل الإرسال: ${call.error ?: "خطأ في الشبكة"}"
                    }
                    val statusColor = when (call.dispatchState) {
                        com.example.model.CallDispatchState.DELIVERED -> Emerald400
                        com.example.model.CallDispatchState.QUEUED,
                        com.example.model.CallDispatchState.SIGNALR_ACCEPTED -> Cyan400
                        com.example.model.CallDispatchState.SERVER_REJECTED,
                        com.example.model.CallDispatchState.FAILED -> com.example.ui.theme.Rose500
                        else -> Amber400
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp
                    )
                }
            }

            // Customer Context details from Taloola POS
            val context = call.customerContext
            if (context != null && (!context.name.isNullOrBlank() || !context.customerId.isNullOrBlank())) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Customer Name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = context.name ?: "عميل مسجل",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Address / Area
                        if (!context.area.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = context.area,
                                    color = Slate400,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Orders stats
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "الطلبات: ${context.orderCount ?: 0} | آخر طلب: ${context.lastOrder ?: "لا يوجد"}",
                                color = Slate400,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "بانتظار جلب بيانات العميل وتاريخ الطلبات من الخادم...",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }
        }
    }
}
