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
    primary = BrightCoral,
    secondary = NeonTurquoise,
    tertiary = AmberGold,
    background = DarkMarineBg,
    surface = DarkMarineCard,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = LightGrayText,
    onSurface = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightSkyPrimary,
    secondary = LightSkySecondary,
    tertiary = LightSkyTertiary,
    background = LightSkyBg,
    surface = LightSkyCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1E3A8A), // Deep Navy for light-mode text readability
    onSurface = Color(0xFF1E3A8A)
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
      // Avoid dynamicColor setting to override our custom eye-catchy custom branding requested by the user
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
