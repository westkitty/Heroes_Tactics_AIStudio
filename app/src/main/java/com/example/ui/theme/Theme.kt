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
    primary = GoldPrimary,
    onPrimary = CastleNavyDark,
    primaryContainer = GoldContainer,
    onPrimaryContainer = OnGoldContainer,
    secondary = GoldSecondary,
    onSecondary = CastleNavyDark,
    tertiary = CrimsonAccent,
    background = CastleNavyDark,
    onBackground = Color(0xFFF1F5F9),
    surface = CastleSurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = CastleSurfaceLight,
    onSurfaceVariant = Color(0xFFE2E8F0)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.White,
    primaryContainer = OnGoldContainer,
    onPrimaryContainer = GoldContainer,
    secondary = GoldSecondary,
    onSecondary = CastleNavyDark,
    tertiary = CrimsonAccent,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onBackground = CastleNavyDark,
    onSurface = CastleNavyDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
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
