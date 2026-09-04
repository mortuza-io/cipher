package com.rork.cipher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rork.cipher.data.CryptoBox
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated

private const val GRID = 5

/**
 * Deterministic monochrome identicon derived from the username hash.
 * The same username always renders the same cipher pattern.
 */
@Composable
fun IdenticonAvatar(
    username: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val cells = remember(username) { patternFor(username) }
    val strong = SignalGreen
    val soft = SoftMint
    val faint = Hairline
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceElevated)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cell = this.size.width / (GRID + 1f)
            val origin = cell / 2f
            cells.forEachIndexed { index, tone ->
                if (tone == 0) return@forEachIndexed
                val row = index / GRID
                val col = index % GRID
                drawRect(
                    color = when (tone) {
                        1 -> strong
                        2 -> soft
                        else -> faint
                    },
                    topLeft = Offset(origin + col * cell, origin + row * cell),
                    size = Size(cell * 0.86f, cell * 0.86f)
                )
            }
        }
    }
}

private fun patternFor(username: String): IntArray {
    val hash = CryptoBox.sha256Hex(username)
    val cells = IntArray(GRID * GRID)
    for (row in 0 until GRID) {
        for (col in 0..GRID / 2) {
            val nibble = hash[(row * 3 + col) % hash.length].digitToIntOrNull(16) ?: 0
            val tone = when {
                nibble > 11 -> 2
                nibble > 6 -> 1
                nibble > 4 -> 3
                else -> 0
            }
            cells[row * GRID + col] = tone
            cells[row * GRID + (GRID - 1 - col)] = tone
        }
    }
    return cells
}
