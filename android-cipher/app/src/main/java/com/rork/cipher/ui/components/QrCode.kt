package com.rork.cipher.ui.components

import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.TextPrimary

/**
 * A QR code drawn module by module.
 *
 * The card is deliberately light so a camera can read it across a table in a
 * dim room, and it is framed in signal green so it still belongs to Cipher.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    foreground: Color = Canvas,
    background: Color = TextPrimary,
    frame: Color = SignalGreen
) {
    val modules = remember(content) { modulesFor(content) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, frame.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        if (modules != null) {
            DrawCanvas(modifier = Modifier.aspectRatio(1f)) {
                val count = modules.size
                val cell = size.minDimension / count
                for (y in 0 until count) {
                    val row = modules[y]
                    for (x in row.indices) {
                        if (!row[x]) continue
                        drawRect(
                            color = foreground,
                            topLeft = Offset(x * cell, y * cell),
                            size = Size(cell, cell)
                        )
                    }
                }
            }
        }
    }
}

/** Encodes to a square grid of modules, one boolean per dark square. */
private fun modulesFor(content: String): Array<BooleanArray>? = runCatching {
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    Array(bits.height) { y -> BooleanArray(bits.width) { x -> bits[x, y] } }
}.getOrNull()
