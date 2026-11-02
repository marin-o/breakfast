package mk.bosa.breakfast.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion is skipped, not slowed (MOB-035), when the system's animator duration scale is 0 ("Remove animations") or the
 * app's Motion setting is Reduced ([forced]).
 */
internal fun motionReduced(animatorDurationScale: Float, forced: Boolean = false): Boolean = forced || animatorDurationScale == 0f

/** True when Settings, Motion is Reduced; provided at the root of the app. */
internal val LocalMotionForcedReduced = compositionLocalOf { false }

/** [motionReduced] for this device and the Motion setting, read once per composition scope that asks for it. */
@Composable
internal fun rememberMotionReduced(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val forced = LocalMotionForcedReduced.current
    return remember(resolver, forced) {
        motionReduced(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f), forced)
    }
}
