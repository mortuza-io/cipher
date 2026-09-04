package com.rork.cipher.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rork.cipher.data.CropRect
import com.rork.cipher.data.CropShape
import com.rork.cipher.data.PhotoDraft
import com.rork.cipher.data.PhotoEdit
import com.rork.cipher.ui.components.CropStage
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed

private const val THUMB_EDGE = 320

/**
 * What a picked photo will look like when it lands, before anything is sent.
 *
 * Everything that decides how the batch travels lives on this one screen: the
 * order, the caption, a rotate/frame/look pass over each image, and whether the
 * whole batch goes out locked. Nothing has been encrypted or uploaded yet — the
 * edits are still just a description, applied once at the moment of sending.
 *
 * @param drafts the batch, owned by the caller so "add more" can append to it.
 * @param loadPreview renders one draft the way it will be sent.
 */
@Composable
fun PhotoComposer(
    drafts: List<PhotoDraft>,
    caption: String,
    peerLabel: String,
    loadPreview: suspend (Uri, PhotoEdit, Int) -> Bitmap?,
    onDraftsChange: (List<PhotoDraft>) -> Unit,
    onCaptionChange: (String) -> Unit,
    onAddMore: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var index by remember { mutableIntStateOf(0) }
    if (index > drafts.lastIndex) index = drafts.lastIndex.coerceAtLeast(0)
    val current = drafts.getOrNull(index)
    val locked = drafts.firstOrNull()?.locked == true

    // One bitmap per draft-and-edit, so flipping through a batch or undoing a
    // crop is instant instead of a fresh decode every time.
    val previews = remember { mutableStateMapOf<String, ImageBitmap>() }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }

    // Crop is drawn on the uncropped picture, so the frame can always be opened
    // back up again; the crop itself is only a rectangle until the photo is sent.
    var cropping by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(CropRect.FULL) }
    var cropShape by remember { mutableStateOf(CropShape.FREE) }

    val currentKey = current?.let { keyOf(it) }
    val wholeKey = current?.let { wholeKeyOf(it) }
    LaunchedEffect(currentKey) {
        val draft = current ?: return@LaunchedEffect
        val key = currentKey ?: return@LaunchedEffect
        if (previews.containsKey(key)) return@LaunchedEffect
        previews[key] = loadPreview(draft.uri, draft.edit, 1280)?.asImageBitmap()
            ?: return@LaunchedEffect
    }
    LaunchedEffect(wholeKey, cropping) {
        if (!cropping) return@LaunchedEffect
        val draft = current ?: return@LaunchedEffect
        val key = wholeKey ?: return@LaunchedEffect
        if (previews.containsKey(key)) return@LaunchedEffect
        previews[key] = loadPreview(draft.uri, PhotoEdit(rotation = draft.edit.rotation), 1280)
            ?.asImageBitmap() ?: return@LaunchedEffect
    }
    LaunchedEffect(index) { cropping = false }
    LaunchedEffect(drafts.map { keyOf(it) }) {
        drafts.forEach { draft ->
            val key = keyOf(draft)
            if (thumbs.containsKey(key)) return@forEach
            thumbs[key] = loadPreview(draft.uri, draft.edit, THUMB_EDGE)?.asImageBitmap()
                ?: return@forEach
        }
    }

    fun editCurrent(transform: (PhotoEdit) -> PhotoEdit) {
        val draft = current ?: return
        onDraftsChange(
            drafts.mapIndexed { position, item ->
                if (position == index) item.copy(edit = transform(draft.edit)) else item
            }
        )
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas)
                .statusBarsPadding()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (drafts.size == 1) "Send to @$peerLabel"
                        else "${drafts.size} photos to @$peerLabel",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    MonoKeyText(
                        text = if (drafts.size == 1) "sealed on this phone"
                        else "${index + 1} of ${drafts.size} · sealed on this phone"
                    )
                }
                LockToggle(
                    locked = locked,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDraftsChange(drafts.map { it.copy(locked = !locked) })
                    }
                )
                if (drafts.size > 1) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val next = drafts.filterIndexed { position, _ -> position != index }
                            index = index.coerceAtMost(next.lastIndex.coerceAtLeast(0))
                            onDraftsChange(next)
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Remove this photo",
                            tint = WarningRed
                        )
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                val whole = wholeKey?.let { previews[it] }
                if (cropping) {
                    if (whole != null) {
                        CropStage(
                            image = whole,
                            rect = cropRect,
                            lockedRatio = cropShape.ratio,
                            onRectChange = { cropRect = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = SignalGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    return@Box
                }
                val image = currentKey?.let { previews[it] ?: thumbs[it] }
                val settled by animateFloatAsState(
                    targetValue = if (image != null) 1f else 0f,
                    label = "preview"
                )
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "Photo about to be sent",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .graphicsLayer { alpha = settled }
                    )
                } else {
                    CircularProgressIndicator(
                        color = SignalGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (locked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Canvas.copy(alpha = 0.86f))
                            .border(1.dp, SignalGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        MonoKeyText(text = "locked", color = SignalGreen)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (cropping) {
                val whole = wholeKey?.let { previews[it] }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            cropRect = cropRect.turnedClockwise()
                            editCurrent { it.turned() }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Rotate90DegreesCcw,
                            contentDescription = "Rotate",
                            tint = SignalGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(CropShape.entries.toList()) { _, shape ->
                            ToolChip(
                                label = shape.label,
                                selected = cropShape == shape,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    cropShape = shape
                                    val ratio = shape.ratio
                                    cropRect = if (ratio == null || whole == null) cropRect
                                    else CropRect.centred(ratio, whole.width, whole.height)
                                }
                            )
                        }
                        itemsIndexed(listOf(Unit)) { _, _ ->
                            ToolChip(
                                label = "Reset",
                                selected = false,
                                onClick = {
                                    cropShape = CropShape.FREE
                                    cropRect = CropRect.FULL
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { cropping = false }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Apply crop",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSignal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(SignalGreen)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                editCurrent { it.copy(crop = cropRect.takeUnless { r -> r.isFull }) }
                                cropping = false
                            }
                            .padding(horizontal = 22.dp, vertical = 11.dp)
                    )
                }
                return@Column
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 12.dp, end = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        editCurrent { it.turned() }
                    }
                ) {
                    Icon(
                        Icons.Outlined.Rotate90DegreesCcw,
                        contentDescription = "Rotate",
                        tint = SignalGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                val cropped = current?.edit?.crop?.isFull == false
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (cropped) SignalGreen else SurfaceElevated)
                        .border(
                            1.dp,
                            if (cropped) SignalGreen else Hairline,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            cropRect = current?.edit?.crop ?: CropRect.FULL
                            cropShape = CropShape.FREE
                            cropping = true
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Icon(
                        Icons.Outlined.Crop,
                        contentDescription = null,
                        tint = if (cropped) OnSignal else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (cropped) "Cropped" else "Crop",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cropped) OnSignal else TextSecondary
                    )
                }
                if (cropped) {
                    ToolChip(
                        label = "Undo crop",
                        selected = false,
                        onClick = { editCurrent { it.copy(crop = null) } }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            run {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(drafts, key = { _, draft -> draft.uri.toString() }) { position, draft ->
                        val selected = position == index
                        val size by animateDpAsState(
                            targetValue = if (selected) 62.dp else 54.dp,
                            animationSpec = spring(),
                            label = "thumb"
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(size)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceElevated)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) SignalGreen else Hairline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { index = position }
                        ) {
                            val thumb = thumbs[keyOf(draft)]
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(11.dp))
                                )
                            }
                        }
                    }
                    itemsIndexed(listOf(Unit)) { _, _ ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                                .clickable(onClick = onAddMore)
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "Add more photos",
                                tint = SignalGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = locked, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = "They can open it, but not keep it: no saving, no forwarding, and Android blocks the screenshot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftMint,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 132.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, Hairline, RoundedCornerShape(26.dp))
                        .padding(horizontal = 18.dp, vertical = 15.dp)
                ) {
                    if (caption.isEmpty()) {
                        Text(
                            text = "Add a caption",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = onCaptionChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                        cursorBrush = SolidColor(SignalGreen),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SignalGreen)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSend()
                        }
                ) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "Send",
                        tint = OnSignal,
                        modifier = Modifier.size(22.dp)
                    )
                    if (drafts.size > 1) {
                        Text(
                            text = drafts.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = OnSignal,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun keyOf(draft: PhotoDraft): String {
    val crop = draft.edit.crop
    val frame = if (crop == null) "full"
    else "${crop.left},${crop.top},${crop.right},${crop.bottom}"
    return "${draft.uri}|${draft.edit.rotation}|$frame"
}

/** Key for the uncropped rehearsal the crop stage draws on. */
private fun wholeKeyOf(draft: PhotoDraft): String =
    "${draft.uri}|${draft.edit.rotation}|whole"

@Composable
private fun LockToggle(locked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (locked) SignalGreen else SurfaceElevated)
            .border(
                1.dp,
                if (locked) SignalGreen else Hairline,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            contentDescription = if (locked) "Send openly" else "Send locked",
            tint = if (locked) OnSignal else TextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = if (locked) "Locked" else "Open",
            style = MaterialTheme.typography.labelMedium,
            color = if (locked) OnSignal else TextSecondary
        )
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) OnSignal else TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SignalGreen else SurfaceElevated)
            .border(
                1.dp,
                if (selected) SignalGreen else Hairline,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}
