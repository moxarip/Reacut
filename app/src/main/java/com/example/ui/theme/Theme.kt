package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReaCutDarkColorScheme = darkColorScheme(
  primary = ReaCutCyan,
  onPrimary = ReaCutBgDarkest,
  primaryContainer = Color(0xFF004B57),
  onPrimaryContainer = ReaCutCyan,
  secondary = ReaCutTeal,
  onSecondary = ReaCutBgDarkest,
  secondaryContainer = Color(0xFF004D40),
  onSecondaryContainer = ReaCutTeal,
  tertiary = ReaCutAmber,
  onTertiary = ReaCutBgDarkest,
  error = ReaCutPlayheadCoral,
  onError = Color.White,
  background = ReaCutBgPrimary,
  onBackground = ReaCutTextPrimary,
  surface = ReaCutSurface,
  onSurface = ReaCutTextPrimary,
  surfaceVariant = ReaCutSurfaceElevated,
  onSurfaceVariant = ReaCutTextSecondary,
  outline = ReaCutBorder,
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = ReaCutDarkColorScheme,
    typography = Typography,
    content = content,
  )
}
