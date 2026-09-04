package com.rork.cipher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay
import com.rork.cipher.data.ConnectionState
import com.rork.cipher.data.DeliveryState
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.MonoKeyStyle
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed

/** How long a message may be in flight before it counts as stuck. */
private const val SENDING_GRACE_MS = 4_000L

private enum class TickPhase { SENDING, STUCK, SENT, DELIVERED, READ }

/**
 * Receipt ticks in the Telegram idiom: a spinning arc while the message is in
 * flight, one check once the hub has it, two checks once the peer's device has
 * it, and two solid accent checks once they have actually read it. The clock
 * only appears once a message has genuinely failed to leave the device.
 */
@Composable
fun DeliveryTicks(
    state: DeliveryState,
    modifier: Modifier = Modifier,
    onAccent: Boolean = false,
    pendingSince: Long = 0L
) {
    val stuck by produceState(initialValue = false, state, pendingSince) {
        if (state != DeliveryState.PENDING) {
            value = false
            return@produceState
        }
        val waited = System.currentTimeMillis() - pendingSince
        val remaining = SENDING_GRACE_MS - waited
        if (pendingSince <= 0L || remaining <= 0L) {
            value = true
        } else {
            value = false
            delay(remaining)
            value = true
        }
    }
    val phase = when (state) {
        DeliveryState.PENDING -> if (stuck) TickPhase.STUCK else TickPhase.SENDING
        DeliveryState.SENT -> TickPhase.SENT
        DeliveryState.DELIVERED -> TickPhase.DELIVERED
        DeliveryState.READ -> TickPhase.READ
    }
    val faded = if (onAccent) OnSignal.copy(alpha = 0.45f) else TextSecondary
    val solid = if (onAccent) OnSignal else SignalGreen
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)) togetherWith fadeOut(tween(120))
        },
        label = "ticks",
        modifier = modifier
    ) { target ->
        when (target) {
            TickPhase.SENDING -> SendingArc(tint = faded)
            TickPhase.STUCK -> Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = "waiting to send",
                tint = faded,
                modifier = Modifier.size(13.dp)
            )
            TickPhase.SENT -> Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "sent",
                tint = faded,
                modifier = Modifier.size(12.dp)
            )
            TickPhase.DELIVERED -> Icon(
                imageVector = Icons.Outlined.DoneAll,
                contentDescription = "delivered",
                tint = if (onAccent) OnSignal.copy(alpha = 0.72f) else TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            TickPhase.READ -> Icon(
                imageVector = Icons.Outlined.DoneAll,
                contentDescription = "read",
                tint = solid,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** A hairline arc that spins while a message is on its way to the hub. */
@Composable
private fun SendingArc(tint: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sending")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    DrawCanvas(modifier = modifier.size(12.dp).rotate(angle)) {
        val stroke = 1.4.dp.toPx()
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/** Telegram-style "unread" rule inserted where the last session left off. */
@Composable
fun UnreadDivider(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = SignalGreen.copy(alpha = 0.4f))
        Text(
            text = if (count == 1) "1 unread message" else "$count unread messages",
            style = MonoKeyStyle,
            color = SignalGreen,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = SignalGreen.copy(alpha = 0.4f))
    }
}

/** Key material: monospace, letter-spaced, soft mint. */
@Composable
fun MonoKeyText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = SoftMint
) {
    Text(text = text, style = MonoKeyStyle, color = color, modifier = modifier)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = TextSecondary,
        modifier = modifier
    )
}

/** The date badge that opens each day of a conversation. */
@Composable
fun DayDivider(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        DayPill(label = label)
    }
}

/** Same badge, also floated over the thread while it is being scrolled. */
@Composable
fun DayPill(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(SurfaceElevated)
            .border(1.dp, Hairline, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
fun OnlineDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(SignalGreen)
    )
}

/** Three-dot typing indicator used in the conversation header. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(560, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(SignalGreen)
            )
        }
    }
}

@Composable
fun CipherFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Canvas,
            labelColor = TextPrimary,
            selectedContainerColor = SignalGreen,
            selectedLabelColor = OnSignal
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Hairline,
            selectedBorderColor = SignalGreen
        ),
        modifier = modifier
    )
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SignalGreen)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = OnSignal
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    footnote: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        if (footnote != null) {
            Spacer(Modifier.height(16.dp))
            MonoKeyText(text = footnote, color = SoftMint.copy(alpha = 0.5f))
        }
    }
}

/** Slim line that appears whenever the hub connection is not live. */
@Composable
fun ConnectionBanner(
    state: ConnectionState,
    hasNetwork: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state != ConnectionState.ONLINE,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        // With a network present the app is always mid-retry, so the honest
        // state is "reconnecting"; red is reserved for having no network at all.
        val connecting = hasNetwork
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (connecting) Hairline else WarningRed.copy(alpha = 0.16f))
                .padding(vertical = 7.dp)
        ) {
            if (connecting) {
                PulseDot()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (connecting) "reconnecting…"
                else "no network · messages will send when you're back",
                style = MonoKeyStyle,
                color = if (connecting) SoftMint else WarningRed
            )
        }
    }
}

@Composable
private fun PulseDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = modifier
            .size(6.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(SoftMint)
    )
}
