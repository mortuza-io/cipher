package com.rork.cipher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rork.cipher.data.VoicePlayback
import com.rork.cipher.data.VoiceRef
import com.rork.cipher.data.formatDuration
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed
import kotlin.math.abs

private const val FALLBACK_BARS = 34

/**
 * A voice note inside a message bubble.
 *
 * The waveform is drawn from levels captured while recording, so the shape of
 * the recording is visible before any audio has been fetched — the bar chart is
 * not decoration, it is the only preview a sealed note can have. Played bars
 * fill behind the playhead and the whole strip is draggable to scrub.
 *
 * @param playback state of this note when it is the one that is open, else null.
 */
@Composable
fun VoiceNote(
    ref: VoiceRef,
    outgoing: Boolean,
    playback: VoicePlayback?,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bars = remember(ref.blob, ref.levels) {
        if (ref.levels.isNotEmpty()) ref.levels else fallbackBars(ref.blob)
    }
    val playing = playback?.playing == true
    val loading = playback?.loading == true

    // While dragging, the finger owns the playhead so it cannot fight the
    // sixty-times-a-second position updates coming back from the player.
    var scrub by remember(ref.blob) { mutableFloatStateOf(-1f) }
    val progress = when {
        scrub >= 0f -> scrub
        playback != null -> playback.progress
        else -> 0f
    }
    val shown by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(if (scrub >= 0f) 0 else 90, easing = LinearEasing),
        label = "voiceProgress"
    )

    val ink = if (outgoing) OnSignal else TextPrimary
    val active = if (outgoing) OnSignal else SignalGreen
    val idle = if (outgoing) OnSignal.copy(alpha = 0.34f) else TextSecondary.copy(alpha = 0.5f)
    val elapsed = playback?.positionMs ?: 0L

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.width(232.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (outgoing) OnSignal.copy(alpha = 0.16f) else SignalGreen)
                .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = if (outgoing) OnSignal else OnSignal,
                    strokeWidth = 1.6.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play voice message",
                    tint = if (outgoing) OnSignal else OnSignal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                // A tap seeks, a drag scrubs, and a long press still belongs to
                // the bubble behind it.
                .pointerInput(ref.blob) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                        onTap = { offset ->
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            if (playback == null) onToggle() else onSeek(fraction)
                        }
                    )
                }
                .pointerInput(ref.blob, playback == null) {
                    if (playback == null) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset: Offset ->
                            scrub = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (scrub >= 0f) onSeek(scrub)
                            scrub = -1f
                        },
                        onDragCancel = { scrub = -1f }
                    ) { change, _ ->
                        scrub = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                }
        ) {
            Waveform(
                bars = bars,
                progress = shown,
                active = active,
                idle = idle,
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
        }
        Spacer(Modifier.width(10.dp))
        MonoKeyText(
            text = if (playback != null && (playing || elapsed > 0L)) formatDuration(elapsed)
            else ref.clock,
            color = if (outgoing) ink.copy(alpha = 0.75f) else SoftMint
        )
    }
}

@Composable
private fun Waveform(
    bars: List<Int>,
    progress: Float,
    active: Color,
    idle: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        val gap = size.width / (bars.size * 1.9f)
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1.2f)
        val playedTo = size.width * progress
        bars.forEachIndexed { index, level ->
            val x = index * (barWidth + gap)
            val ratio = (level.coerceIn(0, 15) / 15f)
            val height = (size.height * (0.16f + ratio * 0.84f)).coerceAtLeast(2.5f)
            val top = (size.height - height) / 2f
            drawRoundRect(
                color = if (x + barWidth / 2f <= playedTo) active else idle,
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

/**
 * A stable shape for a note recorded by an older build that carried no levels.
 * Derived from the blob id so the same note always looks the same.
 */
private fun fallbackBars(seed: String): List<Int> {
    var state = seed.hashCode().toLong() or 1L
    return (0 until FALLBACK_BARS).map {
        state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        4 + (abs((state shr 33).toInt()) % 10)
    }
}

/**
 * The composer while a finger is held on the microphone.
 *
 * It answers the two questions being asked mid-hold: how long have I been
 * talking, and is it listening — hence the live meter rather than a static
 * icon. Sliding away from the button turns the whole strip into the warning
 * colour, so cancelling is never a surprise.
 */
@Composable
fun RecordingBar(
    elapsedMs: Long,
    level: Int,
    cancelling: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "recording")
    val blink by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    val meter by animateFloatAsState(
        targetValue = (level.coerceIn(0, 15) / 15f),
        animationSpec = tween(110, easing = LinearEasing),
        label = "meter"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(WarningRed.copy(alpha = if (cancelling) 1f else blink))
        )
        Spacer(Modifier.width(12.dp))
        MonoKeyText(
            text = formatDuration(elapsedMs),
            color = if (cancelling) WarningRed else TextPrimary
        )
        Spacer(Modifier.width(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f)
        ) {
            repeat(9) { index ->
                val threshold = index / 9f
                val lit = meter > threshold
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height((7 + index * 2).dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                cancelling -> WarningRed.copy(alpha = 0.25f)
                                lit -> SignalGreen
                                else -> TextSecondary.copy(alpha = 0.28f)
                            }
                        )
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        MonoKeyText(
            text = if (cancelling) "release to cancel" else "slide to cancel",
            color = if (cancelling) WarningRed else TextSecondary
        )
    }
}
