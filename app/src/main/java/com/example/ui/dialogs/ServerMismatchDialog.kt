package com.example.ui.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ServerMismatchDetails
import com.example.ui.theme.Amber400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun ServerMismatchDialog(
    details: ServerMismatchDetails,
    hasExistingTrust: Boolean,
    onConfirmRebind: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Slate900,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "اختلاف في معرف الخادم",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "معرف الخادم الحالي يختلف عن المعرف المخزن في رمز QR.",
                    fontSize = 13.sp,
                    color = Amber400,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "المطعم: ${details.restaurantName}",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "الرابط: ${details.serverUrl}",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                        Text(
                            text = "ServerId المكتشف: ${details.currentServerId}",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                        Text(
                            text = "ServerId في QR: ${details.qrServerId}",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }

                if (hasExistingTrust) {
                    Surface(
                        color = Rose500.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "تنبيه: الموافقة ستقوم بتبديل الارتباط بالخادم الجديد ومسح بيانات الخادم القديم.",
                            fontSize = 12.sp,
                            color = Rose500,
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRebind,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber400,
                    contentColor = Slate950
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_rebind_button")
            ) {
                Text(
                    text = "إعادة الارتباط بالخادم الجديد",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(text = "إلغاء", fontSize = 12.sp)
            }
        }
    )
}
