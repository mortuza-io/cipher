package com.rork.cipher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.cipher.data.FileRef
import com.rork.cipher.ui.theme.LocalPalette

/**
 * A shared file inside a bubble.
 *
 * The card states the file's own facts — its type, its name and its size — and
 * carries one action disc that changes with what the file needs: a download
 * arrow while it is still sealed on the server, a spinner while it travels, a
 * tick once it is on the phone and tapping opens it.
 */
@Composable
fun FileCard(
    file: FileRef,
    outgoing: Boolean,
    ready: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val ink = if (outgoing) palette.onSignal else palette.textPrimary
    val quiet = if (outgoing) palette.onSignal.copy(alpha = 0.66f) else palette.textSecondary
    val disc = if (outgoing) palette.onSignal.copy(alpha = 0.16f) else palette.signal.copy(alpha = 0.14f)
    val press by animateFloatAsState(
        targetValue = if (loading) 0.94f else 1f,
        label = "file-press"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.widthIn(min = 200.dp, max = 268.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .scale(press)
                .clip(RoundedCornerShape(14.dp))
                .background(disc)
                .border(
                    1.dp,
                    if (outgoing) palette.onSignal.copy(alpha = 0.2f) else palette.hairline,
                    RoundedCornerShape(14.dp)
                )
        ) {
            when {
                loading -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = if (outgoing) palette.onSignal else palette.signal,
                    modifier = Modifier.size(20.dp)
                )

                ready -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (outgoing) palette.onSignal else palette.signal,
                    modifier = Modifier.size(20.dp)
                )

                else -> Icon(
                    Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    tint = if (outgoing) palette.onSignal else palette.signal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // The extension is the file's identity, so it is set in the
                // same monospace Cipher uses for every other piece of hard fact.
                Text(
                    text = file.kind,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outgoing) palette.onSignal else palette.softMint,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Dot(quiet)
                Text(
                    text = file.readableSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = quiet,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}
