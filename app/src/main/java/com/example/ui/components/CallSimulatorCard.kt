package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun CallSimulatorCard(
    onSimulateCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var phoneNumberInput by remember { mutableStateOf("07701234567") }
    val presets = listOf(
        "07701234567",
        "07809876543",
        "07505551234"
    )

    Surface(
        color = Slate900,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Slate700, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "محاكاة واختبار مكالمة هاتفية",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "يولّد CallId ثابت ويستدعي RecordCallerCall على خادم Taloola عبر SignalR",
                fontSize = 11.sp,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Presets row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    SuggestionChip(
                        onClick = { phoneNumberInput = preset },
                        label = {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                color = if (phoneNumberInput == preset) Cyan400 else Slate400
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (phoneNumberInput == preset) Slate800 else Slate900
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = if (phoneNumberInput == preset) Cyan400 else Slate700
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input and Call Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = phoneNumberInput,
                    onValueChange = { phoneNumberInput = it },
                    placeholder = { Text("أدخل رقم الهاتف للتجربة", color = Slate400, fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Slate800,
                        unfocusedContainerColor = Slate800,
                        focusedBorderColor = Cyan400,
                        unfocusedBorderColor = Slate700
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulate_phone_input")
                )

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = {
                        if (phoneNumberInput.isNotBlank()) {
                            onSimulateCall(phoneNumberInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan500,
                        contentColor = Slate950
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("simulate_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "محاكاة الرنين",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
