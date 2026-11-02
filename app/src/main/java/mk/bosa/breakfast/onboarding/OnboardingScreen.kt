package mk.bosa.breakfast.onboarding

import mk.bosa.breakfast.ui.components.DraftParts
import mk.bosa.breakfast.ui.components.DraftLine
import mk.bosa.breakfast.domain.LogDraftView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.app.Surfaces
import mk.bosa.breakfast.domain.Goal
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.profile.GOAL_CHOICES
import mk.bosa.breakfast.ui.components.ButtonKind
import mk.bosa.breakfast.ui.components.ChoiceGrid
import mk.bosa.breakfast.ui.components.MacroBarsChart
import mk.bosa.breakfast.ui.components.TotalRow
import mk.bosa.breakfast.ui.components.curveHeight
import mk.bosa.breakfast.ui.components.DayTotal
import mk.bosa.breakfast.ui.components.FoodRow
import mk.bosa.breakfast.ui.components.PrimaryButton
import mk.bosa.breakfast.ui.components.SecondaryButton
import mk.bosa.breakfast.ui.components.StepDots
import mk.bosa.breakfast.ui.components.StepPager
import mk.bosa.breakfast.ui.components.SubScreen
import mk.bosa.breakfast.ui.components.TertiaryButton
import mk.bosa.breakfast.ui.components.inert
import mk.bosa.breakfast.ui.screens.formatKcal
import mk.bosa.breakfast.ui.screens.previewQuantityLine
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

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
        if (!words.pointsAtExample) PageWords(words)
        // Real components showing fixed values: it takes no tap, while a swipe that starts on it still turns the page.
        Box(Modifier.fillMaxWidth().padding(top = if (words.pointsAtExample) 0.dp else 24.dp).inert()) { OnboardingExample(page) }
        if (words.pointsAtExample) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = null, tint = colors.ink, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).size(22.dp))
            PageWords(words)
        }
    }
}

@Composable
private fun PageWords(words: OnboardingPage) {
    val colors = LocalBreakfastColors.current
    Text(words.title, color = colors.ink, style = BreakfastType.Section, modifier = Modifier.semantics { heading() })
    Text(words.body, color = colors.soft, style = BreakfastType.Body, modifier = Modifier.padding(top = 8.dp))
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
            FoodRow("Chicken breast raw", previewQuantityLine(100.0, MeasureUnit.GRAM), formatKcal(106.0))
            FoodRow("Chili", previewQuantityLine(1.0, MeasureUnit.PORTION), formatKcal(412.0))
        }
        4 -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TotalRow("Daily burn", SAMPLE_BURN)
            ChoiceGrid(GOAL_CHOICES.map { it.first }, { goal -> GOAL_CHOICES.first { it.first == goal }.second }, Goal.LOSE, {}, label = "Goal")
            DayTotal(formatKcal(SAMPLE_DAY_KCAL), sampleMacroStats())
        }
        else -> MacroBarsChart(sampleWeek(), showGoal = true, Modifier.fillMaxWidth().height(curveHeight()))
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
