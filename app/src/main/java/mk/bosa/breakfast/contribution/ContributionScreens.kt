package mk.bosa.breakfast.contribution

import mk.bosa.breakfast.ui.components.DecimalField
import mk.bosa.breakfast.ui.components.NutritionFacts
import mk.bosa.breakfast.ui.components.FactRow
import mk.bosa.breakfast.ui.components.StatusBlock
import mk.bosa.breakfast.ui.components.SubScreen
import mk.bosa.breakfast.ui.macroText
import mk.bosa.breakfast.ui.screens.basisLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.components.SecondaryButton
import mk.bosa.breakfast.data.OffContributionDraft
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.components.Hairline
import mk.bosa.breakfast.ui.components.SectionHeading
import mk.bosa.breakfast.ui.pretty
import mk.bosa.breakfast.ui.components.ChoiceOption
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.ui.components.Field
import mk.bosa.breakfast.ui.components.PrimaryButton
import mk.bosa.breakfast.ui.components.ButtonKind
import mk.bosa.breakfast.ui.components.TertiaryButton

/** The 48dp outline under a scanned product's log, as M-Food-Detail draws it. */
@Composable internal fun ContributionAction(contribution: ContributionFlow) {
    SecondaryButton("Contribute to Open Food Facts", contribution::review)
}

@Composable internal fun ContributionReviewScreen(contribution: ContributionFlow, draft: OffContributionDraft) {
    val colors = LocalBreakfastColors.current
    val normalized = draft.normalizedNutrition
    SubScreen(
        "Contribute", contribution::dismissReview, scroll = false,
        bottom = {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton("Continue to sign in", contribution::confirm, kind = ButtonKind.Neutral)
                TertiaryButton("Decline", contribution::dismissReview, Modifier.fillMaxWidth(), soft = true)
            }
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(listOfNotNull(draft.name, draft.brand).joinToString(", "), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 4.dp))
            Hairline(Modifier.padding(top = 16.dp))
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                SectionHeading("Will send")
                if (draft.createNewProduct) {
                    FactRow("Name", draft.name)
                    draft.brand?.let { FactRow("Brand", it) }
                }
                FactRow("Barcode", draft.barcode)
                Text(basisLabel(100.0, draft.basisUnit), color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 10.dp))
                NutritionFacts(
                    normalized.calories.pretty(),
                    fat = macroText(normalized.fat) to "g",
                    carbs = macroText(normalized.carbohydrates) to "g",
                    protein = macroText(normalized.protein) to "g",
                    marked = false,
                )
                if (!draft.createNewProduct) Text("Existing product: only nutrition changes.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 16.dp))
            }
            if (draft.createNewProduct) {
                Text("Language", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 24.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceOption("EN", contribution.languageCode == "en", { contribution.onLanguage("en") }, Modifier.widthIn(min = 64.dp))
                    ChoiceOption("MK", contribution.languageCode == "mk", { contribution.onLanguage("mk") }, Modifier.widthIn(min = 64.dp))
                }
                if (draft.basisUnit == MeasureUnit.MILLILITER) DecimalField("Package quantity, ml", contribution.packageQuantityMl, contribution::onPackageQuantityMl, Modifier.padding(top = 16.dp))
                Text("New product: Breakfast creates it.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 12.dp))
            }
            Hairline(Modifier.padding(top = 24.dp))
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                SectionHeading("Not sent")
                Text("Amounts, aliases, servings", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable internal fun ContributionSignInScreen(contribution: ContributionFlow) {
    val colors = LocalBreakfastColors.current
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { passwordFocus.requestFocus() }
    SubScreen("Sign in", contribution::dismissAuth) {
        Text("Open Food Facts account", color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 16.dp))
        Field("Username", contribution.username, contribution::onUsername, Modifier.padding(top = 16.dp))
        Field(
            "Password", contribution.password, contribution::onPassword, Modifier.padding(top = 16.dp),
            fieldModifier = Modifier.focusRequester(passwordFocus),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { contribution.submit() }),
        )
        Text("Password is not saved.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 12.dp))
        PrimaryButton(if (contribution.busy) "Submitting" else "Submit", contribution::submit, Modifier.padding(top = 24.dp), enabled = !contribution.busy, kind = ButtonKind.Neutral)
        // The arrow, Cancel and system Back are one action, also while it submits ([ContributionFlow.dismissAuth]).
        TertiaryButton("Cancel", contribution::dismissAuth, Modifier.fillMaxWidth().padding(top = 4.dp), soft = true)
    }
}

@Composable internal fun ContributionFailureScreen(contribution: ContributionFlow, message: String) {
    val colors = LocalBreakfastColors.current
    SubScreen("Contribute", contribution::dismissFailure, scroll = false) {
        StatusBlock(
            if (contribution.uncertain) "May have been sent" else "Not sent",
            listOf(message, "Your food and entries are saved on this phone."),
            top = 90.dp,
        ) {
            PrimaryButton("Retry", contribution::retry, kind = ButtonKind.Neutral)
            TertiaryButton("Dismiss", contribution::dismissFailure, Modifier.fillMaxWidth(), soft = true)
        }
    }
}
