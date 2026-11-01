package com.bosa.breakfast.settings

import android.app.UiModeManager
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The "Theme" choice. */
internal enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Whether the app draws dark: the phone's mode ([systemDark]) for System, else the forced one. */
internal fun darkThemeOf(choice: ThemeChoice, systemDark: Boolean): Boolean = when (choice) {
    ThemeChoice.SYSTEM -> systemDark
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

/**
 * The per-app night mode for `UiModeManager.setApplicationNightMode` (API 31+): the system then applies it to the
 * app's configuration and to its launch window. AUTO removes the override, so the app follows the phone.
 */
internal fun applicationNightModeOf(choice: ThemeChoice): Int = when (choice) {
    ThemeChoice.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemeChoice.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeChoice.DARK -> UiModeManager.MODE_NIGHT_YES
}

/**
 * Below API 31: the activity's own uiMode with its night bits forced by [choice], applied as an override configuration
 * before the window exists, so its theme (the `values-night` window background) and Compose both follow. Null for
 * System. The system's launch window cannot be changed there; it keeps the phone's mode.
 */
internal fun forcedUiMode(baseUiMode: Int, choice: ThemeChoice): Int? {
    val night = when (choice) {
        ThemeChoice.SYSTEM -> return null
        ThemeChoice.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        ThemeChoice.DARK -> Configuration.UI_MODE_NIGHT_YES
    }
    return (baseUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
}

/** The "Decimal mark" choice: how numbers are shown. Input reads both marks whatever it is. */
internal enum class DecimalMark { POINT, COMMA }

/** The decimal mark shown now. Snapshot state, so a composition that formatted a number reads it again on a change. */
internal object DisplayDecimal {
    var mark by mutableStateOf(DecimalMark.POINT)
}

/** [text] with every decimal point between two digits shown as [mark]. */
internal fun markDecimals(text: String, mark: DecimalMark): String =
    if (mark == DecimalMark.POINT) text else DECIMAL_POINT.replace(text, ",")

private val DECIMAL_POINT = Regex("""(?<=\d)\.(?=\d)""")

/** The "Motion" choice: Reduced turns motion off as the phone's "Remove animations" does. */
internal enum class MotionChoice { SYSTEM, REDUCED }

/** The "Text size" choice: Larger is one step above the phone's font size, inside the app only. */
internal enum class TextSizeChoice { SYSTEM, LARGER }

/** The step "Larger" adds on top of the phone's font scale. */
internal const val LARGER_TEXT_STEP = 1.15f

/** The font scale the app uses over the phone's [systemFontScale]; null for System (no override). */
internal fun appFontScale(systemFontScale: Float, choice: TextSizeChoice): Float? = when (choice) {
    TextSizeChoice.SYSTEM -> null
    TextSizeChoice.LARGER -> systemFontScale * LARGER_TEXT_STEP
}

/**
 * What the activity overrides in its configuration, in `attachBaseContext` before its window exists, so every window it
 * opens (dialogs and sheets too) and Compose's `LocalDensity` follow: the forced night bits below API 31 (see
 * [forcedUiMode]; from 31 the per-app night mode does it) and the Larger font scale ([appFontScale]).
 */
internal data class ActivityOverride(val uiMode: Int?, val fontScale: Float?)

/** The [ActivityOverride] for these settings; null when nothing is overridden (both System). */
internal fun activityOverrideOf(baseUiMode: Int, systemFontScale: Float, sdk: Int, theme: ThemeChoice, textSize: TextSizeChoice): ActivityOverride? {
    val uiMode = if (sdk < 31) forcedUiMode(baseUiMode, theme) else null
    val fontScale = appFontScale(systemFontScale, textSize)
    return if (uiMode == null && fontScale == null) null else ActivityOverride(uiMode, fontScale)
}
