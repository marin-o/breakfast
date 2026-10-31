package com.iron.breakfast.onboarding

import com.iron.breakfast.ui.components.DraftParts
import com.iron.breakfast.ui.components.DraftLine
import com.iron.breakfast.domain.LogDraftView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.DayTotal
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.StepDots
import com.iron.breakfast.ui.components.StepPager
import com.iron.breakfast.ui.components.SubScreen
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.inert
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.previewQuantityLine
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * The tutorial: a page of [ONBOARDING_PAGES] at a time, with Skip in the bar and Next, or the last page's two ways on, at
 * the foot. The bar has no title: each page's title scrolls with it, so a large font still fits.
 */
@Composable
internal fun OnboardingScreen(onboarding: OnboardingState, surfaces: Surfaces) {
    val position = onboarding.position
    SubScreen(
        "", onboarding::back.takeIf { onboarding.takesBack }, scroll = false,
        trailing = { TertiaryButton("Skip", onboarding::finish) },
        bottom = {
            StepDots(position, Modifier.padding(top = 12.dp))
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (position.arrivedAtLast) {
                    SecondaryButton("Open Profile", surfaces::onboardingToProfile)
                    PrimaryButton("Start logging", surfaces::onboardingToMeals)
                } else {
                    PrimaryButton("Next", onboarding::next, kind = ButtonKind.Neutral)
                }
            }
        },
    ) {
        StepPager(position, onboarding::settled, Modifier.weight(1f).fillMaxWidth()) { page ->
            OnboardingPageContent(page)
        }
    }
}

@Composable
private fun OnboardingPageContent(page: Int) {
    val colors = LocalBreakfastColors.current
    val words = ONBOARDING_PAGES[page]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
        Text(words.title, color = colors.ink, style = BreakfastType.Section, modifier = Modifier.semantics { heading() })
        Text(words.body, color = colors.soft, style = BreakfastType.Body, modifier = Modifier.padding(top = 8.dp))
        // Real components showing fixed values: it takes no tap, while a swipe that starts on it still turns the page.
        Box(Modifier.fillMaxWidth().padding(top = 24.dp).inert()) { OnboardingExample(page) }
    }
}

/** Each page's example, in the components the app draws them with. */
@Composable
private fun OnboardingExample(page: Int) {
    when (page) {
        0 -> Column {
            DraftLine { line -> SampleText(SAMPLE_LINE, line) }
            SampleParts(sampleLineView())
        }
        1 -> Column {
            DraftLine { line -> SampleText(SAMPLE_PARTIAL, line) }
            SampleParts(samplePartialView())
        }
        2 -> Column {
            SampleParts(sampleUnknownView())
            PrimaryButton("Find matches", {}, Modifier.padding(top = 16.dp))
        }
        3 -> Column {
            FoodRow("Ajvar", previewQuantityLine(100.0, MeasureUnit.GRAM), formatKcal(130.0))
            FoodRow("Chili", previewQuantityLine(1.0, MeasureUnit.PORTION), formatKcal(412.0))
        }
        else -> DayTotal(formatKcal(SAMPLE_DAY_KCAL), sampleMacroStats())
    }
}

/** What was typed, in the Log's line. */
@Composable
private fun SampleText(text: String, modifier: Modifier) {
    Text(text, modifier, color = LocalBreakfastColors.current.ink, style = BreakfastType.LargeInput)
}

/** The real parts under the line; the example takes no tap, so nothing is chosen. */
@Composable
private fun SampleParts(view: LogDraftView) = DraftParts(view, onChoose = {}, onResolve = {})
