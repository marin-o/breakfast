package com.iron.breakfast.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The system's animator duration scale is 0 ("Remove animations"): motion is skipped, not slowed (MOB-035). */
internal fun motionReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** [motionReduced] for this device, read once per composition scope that asks for it. */
@Composable
internal fun rememberMotionReduced(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        motionReduced(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}
