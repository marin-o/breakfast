package com.bosa.breakfast.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.bosa.breakfast.R
import com.bosa.breakfast.settings.ThemeChoice
import com.bosa.breakfast.settings.TextSizeChoice
import com.bosa.breakfast.settings.AppSettings
import com.bosa.breakfast.settings.activityOverrideOf
import com.bosa.breakfast.settings.applicationNightModeOf
import com.bosa.breakfast.ui.theme.LightBreakfastColors
import com.bosa.breakfast.ui.theme.DarkBreakfastColors
import com.bosa.breakfast.ui.components.LocalMotionForcedReduced
import com.bosa.breakfast.settings.MotionChoice
import com.bosa.breakfast.ui.components.LocalHaptics
import com.bosa.breakfast.ui.theme.BreakfastTheme
import com.bosa.breakfast.settings.darkThemeOf

class MainActivity : ComponentActivity() {
    /** The Theme and Text size this activity was created with; a new one is applied by [applyTheme] or [applyTextSize]. */
    private var appliedTheme = ThemeChoice.SYSTEM
    private var appliedTextSize = TextSizeChoice.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val settings = AppSettings(newBase)
        appliedTheme = settings.theme
        appliedTextSize = settings.textSize
        // Overridden before the window and its theme exist, so the window background (values-night) and the density of
        // every window follow: below API 31 the forced night mode (there is no per-app one), and the Larger font scale.
        val base = newBase.resources.configuration
        activityOverrideOf(base.uiMode, base.fontScale, Build.VERSION.SDK_INT, appliedTheme, appliedTextSize)?.let { override ->
            applyOverrideConfiguration(Configuration().apply {
                override.uiMode?.let { uiMode = it }
                override.fontScale?.let { fontScale = it }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // API 31+: the system's per-app night mode, which also colours the launch window on the next cold start. The
        // same value again changes nothing; a different one (the setting reset elsewhere) changes the mode in place.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(applicationNightModeOf(appliedTheme))
        }
        super.onCreate(savedInstanceState)
        applySystemBars()
        // The system's contrast scrim is a white veil in light mode, which showed as a white nav bar under `bg`.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            val vm: BreakfastViewModel = viewModel()
            LaunchedEffect(vm.settings.theme) { applyTheme(vm.settings.theme) }
            LaunchedEffect(vm.settings.textSize) { applyTextSize(vm.settings.textSize) }
            CompositionLocalProvider(LocalMotionForcedReduced provides (vm.settings.motion == MotionChoice.REDUCED), LocalHaptics provides vm.settings.haptics) {
                BreakfastTheme(dark = darkThemeOf(vm.settings.theme, isSystemInDarkTheme()), macroPalette = vm.settings.macroColors) { BreakfastApp(vm) }
            }
        }
    }

    /**
     * The mode changed (the Theme setting, or the phone's at System). The manifest keeps the activity through it, since
     * recreating it blanks the screen: Compose follows by itself, the bars and the window background do not.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
        window.setBackgroundDrawableResource(R.color.window_bg)
    }

    /** Keep system bars aligned with the light/dark background token. */
    private fun applySystemBars() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(LightBreakfastColors.bg.toArgb(), DarkBreakfastColors.bg.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(LightBreakfastColors.bg.toArgb(), DarkBreakfastColors.bg.toArgb()),
        )
    }

    /** A new Text size: the activity is recreated, so [attachBaseContext] gives every window the new font scale. */
    private fun applyTextSize(textSize: TextSizeChoice) {
        if (textSize == appliedTextSize) return
        appliedTextSize = textSize
        recreate()
    }

    /**
     * A new Theme setting: API 31+ sets the per-app night mode (the system applies it to the running activity and uses
     * it for the launch window); below that the activity is recreated so [attachBaseContext] applies it.
     */
    private fun applyTheme(theme: ThemeChoice) {
        if (theme == appliedTheme) return
        appliedTheme = theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(applicationNightModeOf(theme))
        } else {
            recreate()
        }
    }
}
