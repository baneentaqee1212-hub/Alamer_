package com.example.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.QrValidationResult
import com.example.network.QrParserAndValidator
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerSheet(
    sheetState: SheetState,
    isPairingInProgress: Boolean,
    expirySecondsRemaining: Long = 0,
    isQrExpired: Boolean = false,
    onDismiss: () -> Unit,
    onPairWithQr: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var manualQrText by remember { mutableStateOf("") }
    var validationResult by remember { mutableStateOf<QrValidationResult?>(null) }
    var isManualInputMode by remember { mutableStateOf(!hasCameraPermission) }

    LaunchedEffect(manualQrText) {
        if (manualQrText.isNotBlank()) {
            validationResult = QrParserAndValidator.validate(manualQrText)
        } else {
            validationResult = null
        }
    }

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
            // Header Row: Branding + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "اقتران خادم Taloola POS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "امسح رمز QR المعروض على شاشة الكاشير لربط البدالة فوراً",
                        fontSize = 12.sp,
                        color = Slate400,
                        modifier = Modifier.padding(start = 34.dp, top = 2.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

            // Expiry Countdown Banner if QR has expiry
            if (validationResult?.data != null) {
                val data = validationResult!!.data!!
                val expSeconds = expirySecondsRemaining
                val minutes = expSeconds / 60
                val seconds = expSeconds % 60
                val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                Surface(
                    color = if (isQrExpired) Rose500.copy(alpha = 0.15f) else Amber400.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isQrExpired) Rose500.copy(alpha = 0.4f) else Amber400.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isQrExpired) Icons.Default.Warning else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isQrExpired) Rose500 else Amber400,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isQrExpired) "الرمز منتهي الصلاحية" else "صالح لمدة ($formattedTime)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isQrExpired) Rose500 else Amber400
                            )
                        }
                        Text(
                            text = "المطعم: ${data.name}",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Mode switch tabs (Camera vs Manual Paste)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate800)
                    .padding(4.dp)
            ) {
                Surface(
                    color = if (!isManualInputMode) Cyan500 else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp)),
                    onClick = {
                        isManualInputMode = false
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = if (!isManualInputMode) Slate950 else Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الكاميرا",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isManualInputMode) Slate950 else Slate400
                        )
                    }
                }

                Surface(
                    color = if (isManualInputMode) Cyan500 else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp)),
                    onClick = { isManualInputMode = true }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = if (isManualInputMode) Slate950 else Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "لصق يدوي",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isManualInputMode) Slate950 else Slate400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isManualInputMode) {
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Cyan400, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraPreviewScanner(
                            onQrCodeScanned = { scanned ->
                                manualQrText = scanned
                                validationResult = QrParserAndValidator.validate(scanned)
                                if (validationResult?.isValid == true) {
                                    onPairWithQr(scanned)
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .border(2.dp, Amber400, RoundedCornerShape(12.dp))
                        )
                    }
                } else {
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "مطلوب إذن الكاميرا لمسح رمز QR",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = Slate950),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("منح الإذن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Manual QR Input Section
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = manualQrText,
                onValueChange = { manualQrText = it },
                label = { Text("نص رمز QR الكامل (taloola-caller://pair?...)") },
                placeholder = { Text("taloola-caller://pair?v=1&type=CallerAssistant...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan400,
                    unfocusedBorderColor = Slate700,
                    focusedContainerColor = Slate800,
                    unfocusedContainerColor = Slate800
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_qr_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Test QR generator button for port 5000
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        manualQrText = QrParserAndValidator.buildTestQrString(
                            host = "192.168.68.104",
                            port = 5000,
                            serverId = "TALOOLA-SRV-904",
                            name = "مطعم التجربة"
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("توليد QR تجريبي (192.168.68.104:5000)", fontSize = 11.sp, color = Cyan400)
                }
            }

            // Validation Results Checklist
            if (validationResult != null) {
                Spacer(modifier = Modifier.height(14.dp))
                ValidationSummaryView(result = validationResult!!)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Pairing Button
            Button(
                onClick = {
                    if (validationResult?.isValid == true && manualQrText.isNotBlank()) {
                        onPairWithQr(manualQrText)
                    }
                },
                enabled = validationResult?.isValid == true && !isPairingInProgress && !isQrExpired,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald400,
                    contentColor = Slate950,
                    disabledContainerColor = Slate800,
                    disabledContentColor = Slate400
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_pair_button")
            ) {
                if (isPairingInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Slate950,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جارٍ الاقتران مع Taloola...")
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isQrExpired) "رمز QR منتهي الصلاحية" else "تأكيد الاقتران والبدء",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ValidationSummaryView(result: QrValidationResult) {
    Surface(
        color = Slate800,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (result.isValid) Emerald400.copy(alpha = 0.5f) else Rose500.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (result.isValid) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.isValid) Emerald400 else Rose500,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (result.isValid) "بيانات QR متوافقة تماماً (12/12 فحص)" else "فحوصات رمز QR غير مكتملة",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.isValid) Emerald400 else Rose500
                )
            }
            if (!result.isValid && result.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                result.errors.take(3).forEach { err ->
                    Text(
                        text = "• $err",
                        fontSize = 11.sp,
                        color = Rose500
                    )
                }
            }
            if (result.isValid && result.data != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "المطعم: ${result.data.name} | Host: ${result.data.host}:${result.data.port} | SID: ${result.data.serverId}",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun CameraPreviewScanner(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var lastScannedTime by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val multiFormatReader = MultiFormatReader()
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    if (now - lastScannedTime > 1500) {
                        val buffer = imageProxy.planes[0].buffer
                        val data = ByteArray(buffer.remaining())
                        buffer.get(data)
                        val width = imageProxy.width
                        val height = imageProxy.height
                        val source = PlanarYUVLuminanceSource(
                            data, width, height, 0, 0, width, height, false
                        )
                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                        try {
                            val result = multiFormatReader.decodeWithState(bitmap)
                            val qrText = result.text
                            if (qrText != null && qrText.startsWith("taloola-caller")) {
                                lastScannedTime = now
                                onQrCodeScanned(qrText)
                            }
                        } catch (e: Exception) {
                            // No QR found in frame
                        } finally {
                            multiFormatReader.reset()
                        }
                    }
                    imageProxy.close()
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    // Camera binding failed
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
