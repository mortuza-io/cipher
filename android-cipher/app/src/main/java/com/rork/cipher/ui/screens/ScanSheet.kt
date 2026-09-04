package com.rork.cipher.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Reads another phone's Cipher QR code. Only `cipher://` payloads are accepted,
 * so pointing the camera at a random barcode does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        denied = !allowed
    }

    LaunchedEffect(Unit) {
        if (!granted) request.launch(Manifest.permission.CAMERA)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 36.dp)
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = SignalGreen)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Scan a Cipher code",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Point this at the other phone's screen. Nothing leaves your device: the code is read here.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Canvas)
                    .border(1.dp, SignalGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                when {
                    granted -> CameraFeed(onScanned = onScanned)
                    denied -> MonoKeyText(
                        text = "camera permission denied",
                        color = TextSecondary
                    )
                    else -> MonoKeyText(text = "waiting for camera\u2026", color = TextSecondary)
                }
            }
            if (denied) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { request.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera", color = SignalGreen)
                }
            }
        }
    }
}

@Composable
private fun CameraFeed(onScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var failed by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(Unit) {
        val provider = withContext(Dispatchers.IO) {
            runCatching { ProcessCameraProvider.getInstance(context).get() }.getOrNull()
        }
        if (provider == null) {
            failed = true
            return@LaunchedEffect
        }
        val selector = when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                CameraSelector.DEFAULT_BACK_CAMERA

            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                CameraSelector.DEFAULT_FRONT_CAMERA

            else -> null
        }
        if (selector == null) {
            failed = true
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { image ->
            val payload = decode(image)
            image.close()
            if (payload != null && !handled && payload.startsWith("cipher://", true)) {
                handled = true
                onScanned(payload)
            }
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        }.onFailure {
            Log.w("ScanSheet", "camera could not be bound")
            failed = true
        }
    }

    if (failed) {
        MonoKeyText(text = "no camera on this device", color = TextSecondary)
    } else {
        AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
    }
}

private val reader = MultiFormatReader().apply {
    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
}

/** Reads the luminance plane straight out of the camera frame. */
private fun decode(image: ImageProxy): String? = runCatching {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val rowStride = plane.rowStride
    val source = PlanarYUVLuminanceSource(
        data,
        rowStride,
        image.height,
        0,
        0,
        minOf(image.width, rowStride),
        image.height,
        false
    )
    val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
    result?.text
}.getOrNull().also { runCatching { reader.reset() } }
