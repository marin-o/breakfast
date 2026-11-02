package mk.bosa.breakfast.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants

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

/** The standard haptic for the tick on Add and Undo: CONFIRM from API 30, VIRTUAL_KEY before; null when Haptics is off. */
// CONFIRM is only returned for [sdk] 30 and up, which the caller passes as Build.VERSION.SDK_INT.
@SuppressLint("InlinedApi")
internal fun tickHapticFor(enabled: Boolean, sdk: Int): Int? = when {
    !enabled -> null
    sdk >= 30 -> HapticFeedbackConstants.CONFIRM
    else -> HapticFeedbackConstants.VIRTUAL_KEY
}
