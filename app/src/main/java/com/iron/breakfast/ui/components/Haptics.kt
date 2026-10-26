package com.iron.breakfast.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import com.iron.breakfast.settings.tickHapticFor

/** True when Settings, Haptics is On; provided at the root of the app. */
internal val LocalHaptics = compositionLocalOf { false }

/**
 * The short tick for Add and Undo: the platform's standard haptic ([tickHapticFor]) on this view, which needs no
 * permission and follows the phone's own touch-feedback setting. Does nothing while Haptics is Off.
 */
@Composable
internal fun rememberTick(): () -> Unit {
    val view = LocalView.current
    val constant = tickHapticFor(LocalHaptics.current, Build.VERSION.SDK_INT)
    return { if (constant != null) view.performHapticFeedback(constant) }
}
