package com.iron.breakfast.contribution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.BreakfastViewModel
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.domain.decimalInput
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.isLargeFont

/** The 48dp outline under a scanned product's log, as M-Food-Detail draws it. */
@Composable internal fun ContributionAction(vm: BreakfastViewModel) {
    SecondaryButton("Contribute to Open Food Facts", vm.contribution::review)
}

@Composable internal fun ContributionReviewScreen(vm: BreakfastViewModel, draft: OffContributionDraft) {
    val colors = LocalBreakfastColors.current
    val normalized = draft.normalizedNutrition
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter)) {
            TopBar("Contribute", vm.contribution::dismissReview, Modifier.padding(top = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(listOfNotNull(draft.name, draft.brand).joinToString(", "), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 4.dp))
                Hairline(Modifier.padding(top = 16.dp))
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    SectionHeading("Will send")
                    if (draft.createNewProduct) {
                        ContributionLine("Name", draft.name)
                        draft.brand?.let { ContributionLine("Brand", it) }
                    }
                    ContributionLine("Barcode", draft.barcode)
                    Text("per 100 ${draft.basisUnit.label}", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 10.dp))
                    ContributionLine("Energy", normalized.calories.pretty(), "kcal")
                    ContributionLine("Fat", normalized.fat?.pretty() ?: "0", "g")
                    ContributionLine("Carbohydrate", normalized.carbohydrates?.pretty() ?: "0", "g")
                    ContributionLine("Protein", normalized.protein?.pretty() ?: "0", "g")
                    if (!draft.createNewProduct) Text("Existing product: only nutrition changes.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 16.dp))
                }
                if (draft.createNewProduct) {
                    Text("Language", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 24.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceOption("EN", vm.contribution.languageCode == "en", { vm.contribution.onLanguage("en") }, Modifier.widthIn(min = 64.dp))
                        ChoiceOption("MK", vm.contribution.languageCode == "mk", { vm.contribution.onLanguage("mk") }, Modifier.widthIn(min = 64.dp))
                    }
                    if (draft.basisUnit == MeasureUnit.MILLILITER) Field("Package quantity, ml", vm.contribution.packageQuantityMl, { vm.contribution.onPackageQuantityMl(it.decimalInput()) }, Modifier.padding(top = 16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text("New product: Breakfast creates it.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 12.dp))
                }
                Hairline(Modifier.padding(top = 24.dp))
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    SectionHeading("Not sent")
                    Text("Amounts, aliases, servings", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton("Continue to sign in", vm.contribution::confirm, kind = ButtonKind.Neutral)
                TertiaryButton("Decline", vm.contribution::dismissReview, Modifier.fillMaxWidth(), soft = true)
            }
        }
    }
}

@Composable internal fun ContributionSignInScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { passwordFocus.requestFocus() }
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            TopBar("Sign in", vm.contribution::dismissAuth, Modifier.padding(top = 8.dp))
            Text("Open Food Facts account", color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 16.dp))
            Field("Username", vm.contribution.username, vm.contribution::onUsername, Modifier.padding(top = 16.dp))
            Field(
                "Password", vm.contribution.password, vm.contribution::onPassword, Modifier.padding(top = 16.dp),
                fieldModifier = Modifier.focusRequester(passwordFocus),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.contribution.submit() }),
            )
            Text("Password is not saved.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 12.dp))
            PrimaryButton(if (vm.contribution.busy) "Submitting" else "Submit", vm.contribution::submit, Modifier.padding(top = 24.dp), enabled = !vm.contribution.busy, kind = ButtonKind.Neutral)
            // The arrow, Cancel and system Back are one action, also while it submits ([ContributionFlow.dismissAuth]).
            TertiaryButton("Cancel", vm.contribution::dismissAuth, Modifier.fillMaxWidth().padding(top = 4.dp), soft = true)
        }
    }
}

@Composable internal fun ContributionFailureScreen(vm: BreakfastViewModel, message: String) {
    val colors = LocalBreakfastColors.current
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter)) {
            TopBar("Contribute", vm.contribution::dismissFailure, Modifier.padding(top = 8.dp))
            Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (vm.contribution.uncertain) "May have been sent" else "Not sent", color = colors.ink, style = BreakfastType.Title)
                Text(
                    message,
                    color = colors.soft,
                    style = BreakfastType.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
                )
                Text(
                    "Your food and entries are saved on this phone.",
                    color = colors.soft,
                    style = BreakfastType.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).widthIn(max = 290.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton("Retry", vm.contribution::retry, kind = ButtonKind.Neutral)
                TertiaryButton("Dismiss", vm.contribution::dismissFailure, Modifier.fillMaxWidth(), soft = true)
            }
        }
    }
}

@Composable private fun ContributionLine(label: String, value: String, unit: String? = null) {
    val colors = LocalBreakfastColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val valueModifier = if (unit == null) Modifier else Modifier.widthIn(min = 64.dp * fontScale)
    val unitModifier = Modifier.widthIn(min = 44.dp * fontScale).padding(start = 8.dp)
    // Plain values (name, brand, barcode) are soft; amounts are ink with a soft unit.
    val valueColor = if (unit == null) colors.soft else colors.ink
    val valueStyle = if (unit == null) BreakfastType.Body else BreakfastType.BodyMedium
    if (unit != null && isLargeFont(fontScale)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(label, color = colors.ink, style = BreakfastType.Body)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(value, color = valueColor, style = valueStyle, textAlign = TextAlign.End, modifier = valueModifier)
                Text(unit, color = colors.soft, style = BreakfastType.Secondary, maxLines = 1, modifier = unitModifier)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = colors.ink, style = BreakfastType.Body, modifier = Modifier.weight(1f))
            Text(value, color = valueColor, style = valueStyle, textAlign = TextAlign.End, modifier = valueModifier)
            if (unit != null) Text(unit, color = colors.soft, style = BreakfastType.Secondary, maxLines = 1, modifier = unitModifier)
        }
    }
}
