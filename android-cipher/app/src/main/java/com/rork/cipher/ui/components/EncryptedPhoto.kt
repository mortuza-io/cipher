package com.rork.cipher.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import com.rork.cipher.data.PhotoRef
import com.rork.cipher.data.PhotoSaveResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary

/** Loads a photo asynchronously: the inline thumbnail first, the full image after. */
@Composable
private fun rememberPhoto(
    ref: PhotoRef,
    load: suspend (PhotoRef) -> ByteArray?
): Pair<ImageBitmap?, ImageBitmap?> {
    val thumb = remember(ref.thumb) { decodeBase64Image(ref.thumb) }
    var full by remember(ref.blob) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(ref.blob) {
        full = load(ref)?.let { decodeImage(it) }
    }
    return thumb to full
}

private fun decodeBase64Image(encoded: String): ImageBitmap? = runCatching {
    if (encoded.isEmpty()) return null
    decodeImage(Base64.decode(encoded, Base64.NO_WRAP))
}.getOrNull()

private fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

/**
 * A photo inside a message bubble. The blurred thumbnail shows immediately and
 * resolves into the full image once it has been fetched and decrypted.
 */
@Composable
fun EncryptedPhoto(
    ref: PhotoRef,
    load: suspend (PhotoRef) -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A locked photo is not drawn in the thread at all. It opens in a window
    // Android marks secure, and a bubble sitting in the scrollback would be a
    // hole straight through that: one screenshot of the conversation and the
    // photo is out.
    if (ref.locked) {
        LockedPhotoCard(ref = ref, onClick = onClick, onLongClick = onLongClick, modifier = modifier)
        return
    }

    val (thumb, full) = rememberPhoto(ref, load)
    val ratio = ref.aspect.coerceIn(0.6f, 1.8f)
    val revealed by animateFloatAsState(
        targetValue = if (full != null) 1f else 0f,
        label = "reveal"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(248.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (full == null) 18.dp else 0.dp)
            )
        }
        if (full != null) {
            Image(
                bitmap = full,
                contentDescription = "Encrypted photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = revealed }
            )
        }
        AnimatedVisibility(
            visible = full == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DecryptingChip()
        }
    }
}

/** A photo that may be opened but not kept: a sealed plate, not a picture. */
@Composable
private fun LockedPhotoCard(
    ref: PhotoRef,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumb = remember(ref.thumb) { decodeBase64Image(ref.thumb) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(248.dp)
            .aspectRatio(ref.aspect.coerceIn(0.6f, 1.8f))
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .border(1.dp, SignalGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(34.dp)
                    .graphicsLayer { alpha = 0.5f }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Canvas.copy(alpha = 0.86f))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = SignalGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            MonoKeyText(text = "locked photo · tap to view", color = SignalGreen)
        }
    }
}

/** Small square preview used in the shared-photos strip. */
@Composable
fun PhotoThumbnail(
    ref: PhotoRef,
    load: suspend (PhotoRef) -> ByteArray?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (ref.locked) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated)
                .border(1.dp, SignalGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = onClick)
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = "Locked photo",
                tint = SignalGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        return
    }
    val (thumb, full) = rememberPhoto(ref, load)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
    ) {
        val image = full ?: thumb
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Shared photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (full == null) 10.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun DecryptingChip(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Canvas.copy(alpha = 0.82f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CircularProgressIndicator(
            color = SignalGreen,
            strokeWidth = 1.5.dp,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        MonoKeyText(text = "decrypting")
    }
}

/**
 * Full-screen viewer with pinch-to-zoom and drag, on a black canvas.
 *
 * A locked photo opens here and nowhere else: this window is marked secure, so
 * Android refuses the screenshot, the screen recording and the recent-apps
 * thumbnail while it is up, and the save button is not offered at all.
 *
 * @param onSave copies the decrypted photo into the gallery; the one moment a
 *   Cipher photo is written somewhere readable, and only on request.
 */
@Composable
fun PhotoViewer(
    ref: PhotoRef,
    caption: String,
    load: suspend (PhotoRef) -> ByteArray?,
    onSave: suspend () -> PhotoSaveResult,
    onDismiss: () -> Unit
) {
    val (thumb, full) = rememberPhoto(ref, load)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun announce(text: String) {
        scope.launch {
            note = text
            delay(2_400L)
            note = null
        }
    }

    fun save() {
        saving = true
        scope.launch {
            val outcome = onSave()
            saving = false
            announce(
                when (outcome) {
                    PhotoSaveResult.SAVED -> "saved to your gallery"
                    PhotoSaveResult.LOCKED -> "locked photos cannot be saved"
                    PhotoSaveResult.UNREADABLE -> "that photo could not be opened"
                    PhotoSaveResult.WRITE_FAILED -> "your gallery refused the write"
                }
            )
        }
    }

    // Android 9 and older still gate the media store behind a permission, and a
    // photo somebody was sent is worth asking for it at the moment they ask.
    val storage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) save() else announce("gallery access was refused")
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        DisposableEffect(ref.locked) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (ref.locked) {
                window?.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
            onDispose {
                if (ref.locked) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas)
        ) {
            val image = full ?: thumb
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Encrypted photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transform)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .blur(if (full == null) 24.dp else 0.dp)
                )
            }
            if (full == null) DecryptingChip()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (ref.locked) SignalGreen else SoftMint,
                        modifier = Modifier.size(13.dp)
                    )
                    MonoKeyText(
                        text = if (ref.locked) "locked · cannot be saved"
                        else "sealed · ${ref.width}×${ref.height}",
                        color = if (ref.locked) SignalGreen else SoftMint
                    )
                }
                if (!ref.locked) {
                    IconButton(
                        enabled = full != null && !saving,
                        onClick = {
                            val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                            val granted = !legacy || ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) save()
                            else storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                color = SignalGreen,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = "Save to gallery",
                                tint = if (full != null) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = note != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                MonoKeyText(
                    text = note.orEmpty(),
                    color = SignalGreen,
                    modifier = Modifier
                        .padding(top = 66.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Canvas.copy(alpha = 0.9f))
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
        }
    }
}

/** Round badge shown on the composer while an attachment is being sealed. */
@Composable
fun UploadingBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Canvas.copy(alpha = 0.8f))
    ) {
        CircularProgressIndicator(
            color = SignalGreen,
            strokeWidth = 1.5.dp,
            modifier = Modifier.size(14.dp)
        )
    }
}
