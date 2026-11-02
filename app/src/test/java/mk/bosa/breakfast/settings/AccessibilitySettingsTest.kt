package mk.bosa.breakfast.settings

import android.content.res.Configuration
import mk.bosa.breakfast.ui.components.isLargeFont
import mk.bosa.breakfast.ui.components.motionReduced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B9 Accessibility group: Motion, Text size, Haptics. */
class AccessibilitySettingsTest {
    @Test fun reducedMotionTurnsMotionOffWhateverThePhoneSays() {
        assertFalse(motionReduced(1f, forced = false))
        assertTrue(motionReduced(0f, forced = false))
        assertTrue(motionReduced(1f, forced = true))
        assertTrue(motionReduced(0.5f, forced = true))
    }

    @Test fun largerIsOneStepAboveThePhonesFontScale() {
        assertNull(appFontScale(1f, TextSizeChoice.SYSTEM))
        assertEquals(1.15f, appFontScale(1f, TextSizeChoice.LARGER)!!, 1e-6f)
        assertEquals(1.3f * 1.15f, appFontScale(1.3f, TextSizeChoice.LARGER)!!, 1e-6f)
    }

    @Test fun theLargeFontLayoutsTakeOverByTheSameRule() {
        // Larger at the phone's default stays in the normal layouts; on a large phone font it reaches the stacked ones.
        assertFalse(isLargeFont(appFontScale(1f, TextSizeChoice.LARGER)!!))
        assertTrue(isLargeFont(appFontScale(1.6f, TextSizeChoice.LARGER)!!))
        assertTrue(isLargeFont(appFontScale(2f, TextSizeChoice.LARGER)!!))
    }

    @Test fun theActivityOverridesOnlyWhatASettingForces() {
        val light = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
        assertNull(activityOverrideOf(light, 1f, 34, ThemeChoice.SYSTEM, TextSizeChoice.SYSTEM))
        // From API 31 the per-app night mode carries the theme, so the activity does not override it.
        assertNull(activityOverrideOf(light, 1f, 34, ThemeChoice.DARK, TextSizeChoice.SYSTEM))
        assertEquals(ActivityOverride(Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES, null), activityOverrideOf(light, 1f, 30, ThemeChoice.DARK, TextSizeChoice.SYSTEM))
        val larger = activityOverrideOf(light, 1.3f, 34, ThemeChoice.SYSTEM, TextSizeChoice.LARGER)!!
        assertNull(larger.uiMode)
        assertEquals(1.3f * LARGER_TEXT_STEP, larger.fontScale!!, 1e-6f)
    }

    @Test fun accessibilityDefaultsAreTodays() {
        assertEquals(MotionChoice.SYSTEM, enumOf(null, MotionChoice.SYSTEM))
        assertEquals(TextSizeChoice.SYSTEM, enumOf("WHATEVER", TextSizeChoice.SYSTEM))
    }
}
