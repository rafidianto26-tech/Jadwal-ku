package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF818CF8), // Indigo 400
    onPrimary = Color(0xFF1E1B4B), // Dark Indigo
    primaryContainer = Color(0xFF312E81), // Indigo 900
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8), // Celestial Blue
    onSecondary = Color(0xFF0C4A6E),
    secondaryContainer = Color(0xFF075985),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFF43F5E), // Vivid Rose Accent
    background = Color(0xFF0F172A), // Premium Slate Deep Dark
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF4F46E5), // Indigo 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF), // Indigo 100
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF0284C7), // Celestial Blue 600
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFFE11D48), // Rose
    background = Color(0xFFF8FAFC), // Slate 50
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9), // Slate 100
    onSurfaceVariant = Color(0xFF475569)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Custom hand-crafted design is preferred, fallback to system is disableable
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
