package com.rork.cipher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rork.cipher.data.CropRect
import com.rork.cipher.ui.theme.SignalGreen
import kotlin.math.abs
import kotlin.math.min

/** Which part of the frame a finger grabbed: -1 / 0 / 1 on each axis. */
private data class Grip(val x: Int, val y: Int) {
    val isMove: Boolean get() = x == 0 && y == 0
    val isCorner: Boolean get() = x != 0 && y != 0
}

private const val MIN_SIDE = 0.08f

/**
 * The crop stage: the whole picture, with a frame the sender drags into shape.
 *
 * The frame is reported back in fractions of the image, so the same gesture
 * describes a crop on the screen-sized rehearsal and on the full-size original
 * that is actually sealed.
 *
 * @param lockedRatio width ÷ height the frame must keep, or null for freehand.
 */
@Composable
fun CropStage(
    image: ImageBitmap,
    rect: CropRect,
    lockedRatio: Float?,
    onRectChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accent = SignalGreen
    val latest by rememberUpdatedState(rect)
    val onChange by rememberUpdatedState(onRectChange)
    var grip by remember { mutableStateOf<Grip?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val fit = min(boxWidth / image.width, boxHeight / image.height)
        val shownWidth = image.width * fit
        val shownHeight = image.height * fit
        val originX = (boxWidth - shownWidth) / 2f
        val originY = (boxHeight - shownHeight) / 2f
        // Ratio expressed in fractions of the image, so a 1:1 frame stays square
        // on screen even when the picture itself is not.
        val fractionRatio = lockedRatio?.let { it * image.height / image.width }
        val touch = with(density) { 30.dp.toPx() }
        val bracket = with(density) { 20.dp.toPx() }
        val hairline = with(density) { 1.dp.toPx() }
        val thick = with(density) { 3.dp.toPx() }

        fun screenRect(value: CropRect) = Rect(
            left = originX + value.left * shownWidth,
            top = originY + value.top * shownHeight,
            right = originX + value.right * shownWidth,
            bottom = originY + value.bottom * shownHeight
        )

        Image(
            bitmap = image,
            contentDescription = "Photo being cropped",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(image, fractionRatio) {
                    detectDragGestures(
                        onDragStart = { start ->
                            grip = gripAt(start, screenRect(latest), touch, fractionRatio != null)
                            if (grip != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = { grip = null },
                        onDragCancel = { grip = null },
                        onDrag = { change, drag ->
                            val held = grip ?: return@detectDragGestures
                            change.consume()
                            val dx = drag.x / shownWidth
                            val dy = drag.y / shownHeight
                            onChange(dragged(latest, held, dx, dy, fractionRatio))
                        }
                    )
                }
        ) {
            val frame = screenRect(latest)
            val shade = Color.Black.copy(alpha = 0.62f)
            drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, frame.top))
            drawRect(
                shade,
                topLeft = Offset(0f, frame.bottom),
                size = Size(size.width, size.height - frame.bottom)
            )
            drawRect(
                shade,
                topLeft = Offset(0f, frame.top),
                size = Size(frame.left, frame.height)
            )
            drawRect(
                shade,
                topLeft = Offset(frame.right, frame.top),
                size = Size(size.width - frame.right, frame.height)
            )

            val grid = Color.White.copy(alpha = 0.22f)
            for (step in 1..2) {
                val x = frame.left + frame.width * step / 3f
                val y = frame.top + frame.height * step / 3f
                drawLine(grid, Offset(x, frame.top), Offset(x, frame.bottom), hairline)
                drawLine(grid, Offset(frame.left, y), Offset(frame.right, y), hairline)
            }

            drawRect(
                color = Color.White.copy(alpha = 0.65f),
                topLeft = Offset(frame.left, frame.top),
                size = Size(frame.width, frame.height),
                style = Stroke(width = hairline)
            )

            val arm = min(bracket, min(frame.width, frame.height) / 3f)
            listOf(
                Triple(Offset(frame.left, frame.top), 1f, 1f),
                Triple(Offset(frame.right, frame.top), -1f, 1f),
                Triple(Offset(frame.left, frame.bottom), 1f, -1f),
                Triple(Offset(frame.right, frame.bottom), -1f, -1f)
            ).forEach { (corner, sx, sy) ->
                drawLine(
                    accent,
                    corner,
                    Offset(corner.x + arm * sx, corner.y),
                    thick
                )
                drawLine(
                    accent,
                    corner,
                    Offset(corner.x, corner.y + arm * sy),
                    thick
                )
            }
        }
    }
}

/** Nearest grabbed handle, or a move when the finger landed inside the frame. */
private fun gripAt(point: Offset, frame: Rect, touch: Float, cornersOnly: Boolean): Grip? {
    val nearLeft = abs(point.x - frame.left) <= touch
    val nearRight = abs(point.x - frame.right) <= touch
    val nearTop = abs(point.y - frame.top) <= touch
    val nearBottom = abs(point.y - frame.bottom) <= touch
    val insideX = point.x in (frame.left - touch)..(frame.right + touch)
    val insideY = point.y in (frame.top - touch)..(frame.bottom + touch)

    val x = when {
        nearLeft && insideY -> -1
        nearRight && insideY -> 1
        else -> 0
    }
    val y = when {
        nearTop && insideX -> -1
        nearBottom && insideX -> 1
        else -> 0
    }
    if (x != 0 || y != 0) {
        if (cornersOnly && !(x != 0 && y != 0)) {
            // A locked ratio only accepts corners; an edge would break the shape.
            return if (frame.contains(point)) Grip(0, 0) else null
        }
        return Grip(x, y)
    }
    return if (frame.contains(point)) Grip(0, 0) else null
}

/** Applies one drag step to the frame, keeping it inside the picture. */
private fun dragged(
    rect: CropRect,
    grip: Grip,
    dx: Float,
    dy: Float,
    ratio: Float?
): CropRect {
    if (grip.isMove) {
        val shiftX = dx.coerceIn(-rect.left, 1f - rect.right)
        val shiftY = dy.coerceIn(-rect.top, 1f - rect.bottom)
        return CropRect(
            left = rect.left + shiftX,
            top = rect.top + shiftY,
            right = rect.right + shiftX,
            bottom = rect.bottom + shiftY
        )
    }

    if (ratio != null && grip.isCorner) {
        // Width leads, height follows, anchored at the opposite corner.
        val anchorX = if (grip.x == -1) rect.right else rect.left
        val anchorY = if (grip.y == -1) rect.bottom else rect.top
        val movingX = (if (grip.x == -1) rect.left else rect.right) + dx
        var width = abs(anchorX - movingX).coerceAtLeast(MIN_SIDE)
        // Never leave the picture on either axis.
        val roomX = if (grip.x == -1) anchorX else 1f - anchorX
        val roomY = if (grip.y == -1) anchorY else 1f - anchorY
        width = min(width, roomX)
        width = min(width, roomY / ratio)
        val height = width / ratio
        val left = if (grip.x == -1) anchorX - width else anchorX
        val right = left + width
        val top = if (grip.y == -1) anchorY - height else anchorY
        val bottom = top + height
        return CropRect(left, top, right, bottom)
    }

    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom
    if (grip.x == -1) left = (left + dx).coerceIn(0f, right - MIN_SIDE)
    if (grip.x == 1) right = (right + dx).coerceIn(left + MIN_SIDE, 1f)
    if (grip.y == -1) top = (top + dy).coerceIn(0f, bottom - MIN_SIDE)
    if (grip.y == 1) bottom = (bottom + dy).coerceIn(top + MIN_SIDE, 1f)
    return CropRect(left, top, right, bottom)
}
