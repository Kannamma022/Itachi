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
    primary = MinimalPrimary,
    secondary = MinimalPrimaryLight,
    tertiary = MinimalConnectedBg,
    background = MinimalBg,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = MinimalTextDarkest,
    onBackground = MinimalTextDark,
    onSurface = MinimalTextDark,
    surfaceVariant = MinimalSidebarBg,
    onSurfaceVariant = MinimalTextMedium,
    outline = MinimalBorder,
    outlineVariant = MinimalBorderLight
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MinimalPrimary,
    secondary = MinimalPrimaryLight,
    tertiary = MinimalConnectedBg,
    background = MinimalBg,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = MinimalTextDarkest,
    onBackground = MinimalTextDark,
    onSurface = MinimalTextDark,
    surfaceVariant = MinimalSidebarBg,
    onSurfaceVariant = MinimalTextMedium,
    outline = MinimalBorder,
    outlineVariant = MinimalBorderLight
  )


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to preserve the exact Clean Minimalism brand theme
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
