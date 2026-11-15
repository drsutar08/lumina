package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = LuminaVioletLight,
    secondary = LuminaSkyAccent,
    tertiary = Pink80,
    background = Color(0xFF14121C),
    surface = Color(0xFF1B1826),
    surfaceVariant = Color(0xFF252134),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFEDEAF6),
    onSurface = Color(0xFFEDEAF6),
    onSurfaceVariant = Color(0xFFB7B1C9)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LuminaVioletPrimary,
    secondary = LuminaSkyAccent,
    tertiary = LuminaLavenderBackdrop,
    primaryContainer = LuminaLavenderBackdrop,
    secondaryContainer = LuminaInfoStripBlue,
    background = LuminaBackground,
    surface = LuminaSurface,
    surfaceVariant = LuminaSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onPrimaryContainer = LuminaOnSurface,
    onSecondaryContainer = LuminaOnSurface,
    onBackground = LuminaOnSurface,
    onSurface = LuminaOnSurface,
    onSurfaceVariant = LuminaOnSurfaceVariant,
    outline = LuminaOutline,
    outlineVariant = LuminaOutlineVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default to enforce our premium High Density theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
