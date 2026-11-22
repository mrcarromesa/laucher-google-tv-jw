package com.mrcarromesa.tvlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme

private val GlasWhite50 = Color.White.copy(alpha = 0.9f)

/** Base escura "Liquid Glass" (contracts/ui-behavior.md §6). */
private val TvColorScheme =
  ColorScheme(
    primary = GlasWhite50,
    onPrimary = DeepSea,
    primaryContainer = Frost,
    onPrimaryContainer = Ink,
    secondary = Frost,
    onSecondary = Ink,
    secondaryContainer = GlasWhite50,
    onSecondaryContainer = DeepSea,
    tertiary = Frost,
    onTertiary = Ink,
    tertiaryContainer = GlasWhite50,
    onTertiaryContainer = DeepSea,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DeepSea,
    onBackground = Ink,
    surface = DeepSea,
    onSurface = Ink,
    surfaceVariant = Frost,
    onSurfaceVariant = InkMuted,
    surfaceTint = GlasWhite50,
    inversePrimary = Frost,
    inverseSurface = GlassWhite,
    inverseOnSurface = DeepSea,
    border = Frost,
    borderVariant = Frost,
    scrim = Color.Black,
  )

/** Tema escuro base para os painéis translúcidos (contracts/ui-behavior.md §6). */
@Composable
fun TVLauncherTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = TvColorScheme,
    typography = TvTypography,
    content = content,
  )
}
