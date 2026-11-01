package com.bosa.breakfast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.ui.theme.Gutter
import com.bosa.breakfast.ui.theme.LocalBreakfastColors

/**
 * A tab's list: its header and line, the [rows], and a closing line only under rows (an empty list keeps just the
 * header's), clear of the bottom area that floats over the end of the list. [bodyModifier] goes on the list.
 */
@Composable
internal fun TabList(
    title: String,
    subtitle: String,
    hasRows: Boolean,
    modifier: Modifier,
    onBack: (() -> Unit)? = null,
    bodyModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    rows: LazyListScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(LocalBreakfastColors.current.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().then(LocalScreenBody.current).then(bodyModifier).padding(horizontal = Gutter),
            contentPadding = PaddingValues(bottom = BottomAreaClearance),
        ) {
            item {
                TopBar(title, onBack, Modifier.padding(top = 8.dp, bottom = 6.dp), subtitle = subtitle, trailing = trailing)
                Hairline()
            }
            rows()
            if (hasRows) item { Hairline(Modifier.padding(top = 8.dp)) }
        }
    }
}

/** A list row fades in and out as the list changes, and moves to its new place. */
internal fun LazyItemScope.listRowMotion(motion: Motion): Modifier =
    Modifier.animateItem(fadeInSpec = motion.listItem(), placementSpec = motion.listItem(), fadeOutSpec = motion.listItem())

/** A [FoodRow] with its "+" at the end. */
@Composable
internal fun FoodRowWithAdd(
    name: String,
    quantityLine: String,
    calories: String,
    onClick: () -> Unit,
    addDescription: String,
    onAdd: () -> Unit,
    addIcon: ImageVector = Icons.Outlined.Add,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FoodRow(name = name, quantityLine = quantityLine, calories = calories, modifier = Modifier.weight(1f), onClick = onClick)
        AddCircle(contentDescription = addDescription, onClick = onAdd, icon = addIcon)
    }
}
