// @dikaacode
package com.dikacode.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NeoPrimary,
    secondary = NeoSecondary,
    background = NeoBackgroundDark,
    surface = NeoSurfaceDark,
    onPrimary = NeoText,
    onSecondary = NeoTextDark,
    onBackground = NeoTextDark,
    onSurface = NeoTextDark,
    error = NeoDanger,
    outline = NeoBorderDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NeoPrimary,
    secondary = NeoSecondary,
    background = NeoBackground,
    surface = NeoSurface,
    onPrimary = NeoText,
    onSecondary = NeoTextDark,
    onBackground = NeoText,
    onSurface = NeoText,
    error = NeoDanger,
    outline = NeoBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color disabled for neobrutalism
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
