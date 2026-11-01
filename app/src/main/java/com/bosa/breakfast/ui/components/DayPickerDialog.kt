package com.bosa.breakfast.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A day as the date picker counts it: midnight UTC. */
internal fun dayUtcMillis(dayKey: String): Long = LocalDate.parse(dayKey).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day the date picker's [utcMillis] stands for. */
internal fun dayKeyOfUtcMillis(utcMillis: Long): String = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()

/** The one date picker: from the day title and Edit entry. Future days are not selectable, as with Next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerDialog(dayKey: String, todayKey: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val todayMillis = dayUtcMillis(todayKey)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = dayUtcMillis(dayKey),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis == null) onDismiss() else onPick(dayKeyOfUtcMillis(millis))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}
