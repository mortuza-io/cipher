package com.rork.cipher.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun schemeFor(palette: Palette) = with(palette) {
    val base = if (dark) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    base.copy(
        primary = signal,
        onPrimary = onSignal,
        primaryContainer = signal,
        onPrimaryContainer = onSignal,
        secondary = softMint,
        onSecondary = onSignal,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = softMint,
        tertiary = softMint,
        onTertiary = onSignal,
        background = canvas,
        onBackground = textPrimary,
        surface = canvas,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surfaceElevated,
        surfaceContainerLow = surfaceElevated,
        surfaceContainerLowest = canvas,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
        outline = hairline,
        outlineVariant = hairline,
        error = warning,
        onError = onSignal,
        scrim = if (dark) canvas else textPrimary
    )
}

/** Monospace style used for every piece of key material in the app. */
val MonoKeyStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    letterSpacing = 0.8.sp,
    fontWeight = FontWeight.Medium
)

private val CipherTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium)
    )
}

/**
 * Night and day share one structure: only the palette changes, and it crossfades
 * rather than snapping, so flipping the switch reads as the room's lights coming
 * up rather than the app reloading.
 */
@Composable
fun AppTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val target = if (dark) DarkPalette else LightPalette
    val spec = tween<androidx.compose.ui.graphics.Color>(durationMillis = 420)
    val canvas by animateColorAsState(target.canvas, spec, label = "canvas")
    val surfaceElevated by animateColorAsState(target.surfaceElevated, spec, label = "surface")
    val surfaceHigh by animateColorAsState(target.surfaceHigh, spec, label = "surfaceHigh")
    val hairline by animateColorAsState(target.hairline, spec, label = "hairline")
    val textPrimary by animateColorAsState(target.textPrimary, spec, label = "textPrimary")
    val textSecondary by animateColorAsState(target.textSecondary, spec, label = "textSecondary")
    val signal by animateColorAsState(target.signal, spec, label = "signal")
    val softMint by animateColorAsState(target.softMint, spec, label = "softMint")
    val onSignal by animateColorAsState(target.onSignal, spec, label = "onSignal")
    val warning by animateColorAsState(target.warning, spec, label = "warning")

    val palette = Palette(
        canvas = canvas,
        surfaceElevated = surfaceElevated,
        surfaceHigh = surfaceHigh,
        hairline = hairline,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        signal = signal,
        softMint = softMint,
        onSignal = onSignal,
        warning = warning,
        dark = dark
    )

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = CipherTypography,
            content = content
        )
    }
}
