package com.example.focusflow.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** The chosen theme: 0 = system default, 1 = light, 2 = dark. Saved in SettingsStore.themeMode. */
object ThemeState {
    val mode = mutableIntStateOf(0)

    /** Loads the saved choice. Called once in Activity.onCreate, before the first frame is composed. */
    fun load(ctx: Context) { mode.intValue = com.example.focusflow.data.ServiceLocator.settings(ctx).themeMode.coerceIn(0, 2) }

    /** Applies and saves a new choice. */
    fun set(ctx: Context, m: Int) {
        mode.intValue = m
        com.example.focusflow.data.ServiceLocator.settings(ctx).themeMode = m
    }
}

val LocalFF = staticCompositionLocalOf { LightFF }

/** Access to the extra colour tokens: FF.colors.success, FF.colors.warning and so on. */
object FF {
    val colors: FFColors
        @Composable get() = LocalFF.current
}

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeState.mode.intValue) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        // Keep the status bar and navigation bar icons readable in the chosen theme.
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                // Window background follows the chosen theme too, so no light flash shows behind dialogs or while resizing.
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable((if (dark) DarkScheme else LightScheme).background.toArgb()))
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalFF provides if (dark) DarkFF else LightFF) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = FFTypography,
            shapes = FFMaterialShapes
        ) {
            // Sets the default text colour and background for every screen, including full-screen overlays.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
