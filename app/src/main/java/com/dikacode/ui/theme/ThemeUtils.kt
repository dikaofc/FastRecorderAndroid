// @dikaacode
package com.dikacode.ui.theme

import android.app.Activity
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.dikacode.R

/**
 * Shared theme utilities to eliminate duplicated applyThemeUI() across Activities.
 */
object ThemeUtils {

    private const val DARK_BG = "#121212"
    private const val WHITE = "#FFFFFF"
    private const val BLACK = "#0A0A0A"
    private const val SUBTEXT_DARK = "#AAAAAA"
    private const val SUBTEXT_LIGHT = "#666666"

    /**
     * Apply dark/light theme to the given activity's window and root background.
     * Returns a ThemeColors object for further view-specific styling.
     */
    fun applyTheme(activity: Activity, isDark: Boolean, rootBackground: android.view.View) {
        if (isDark) {
            activity.window.statusBarColor = Color.parseColor(DARK_BG)
            rootBackground.setBackgroundColor(Color.parseColor(DARK_BG))
        } else {
            activity.window.statusBarColor = ContextCompat.getColor(activity, R.color.neo_yellow)
            rootBackground.setBackgroundColor(ContextCompat.getColor(activity, R.color.neo_yellow))
        }
    }

    /** Convenience color constants for dark mode */
    object Dark {
        val text = Color.parseColor(WHITE)
        val textSub = Color.parseColor(SUBTEXT_DARK)
        val bg = Color.parseColor(DARK_BG)
    }

    /** Convenience color constants for light mode */
    object Light {
        val text = Color.parseColor(BLACK)
        val textSub = Color.parseColor(SUBTEXT_LIGHT)
    }
}
