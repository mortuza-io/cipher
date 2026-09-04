package com.rork.cipher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One set of Cipher colours. Every screen reads its colours through the
 * top-level tokens below, which resolve against whichever palette is provided,
 * so the whole interface switches together.
 */
@Immutable
data class Palette(
    val canvas: Color,
    val surfaceElevated: Color,
    val surfaceHigh: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val signal: Color,
    val softMint: Color,
    val onSignal: Color,
    val warning: Color,
    val dark: Boolean
)

/** Night: near-black canvas with sharp signal green. */
val DarkPalette = Palette(
    canvas = Color(0xFF0B0F0D),
    surfaceElevated = Color(0xFF161C19),
    surfaceHigh = Color(0xFF1D2521),
    hairline = Color(0xFF222B27),
    textPrimary = Color(0xFFE9F5EF),
    textSecondary = Color(0xFF7E8B85),
    signal = Color(0xFF3DDC97),
    softMint = Color(0xFF8AF5C4),
    onSignal = Color(0xFF08110C),
    warning = Color(0xFFFF6B5B),
    dark = true
)

/**
 * Day: the same architecture in daylight — cool paper canvas, white cards and a
 * deeper green so the signal still carries against white.
 */
val LightPalette = Palette(
    canvas = Color(0xFFEDF2EE),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE1EAE4),
    hairline = Color(0xFFD3DED7),
    textPrimary = Color(0xFF0B1310),
    textSecondary = Color(0xFF5B6A63),
    signal = Color(0xFF0B7A55),
    softMint = Color(0xFF0A6247),
    onSignal = Color(0xFFF4FFF9),
    warning = Color(0xFFC3382B),
    dark = false
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val Canvas: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.canvas

val SurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceElevated

val SurfaceHigh: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceHigh

val Hairline: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.hairline

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary

val SignalGreen: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.signal

val SoftMint: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.softMint

val OnSignal: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.onSignal

val WarningRed: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.warning

/** True when the interface is currently painted dark. */
val IsDarkTheme: Boolean
    @Composable @ReadOnlyComposable get() = LocalPalette.current.dark
