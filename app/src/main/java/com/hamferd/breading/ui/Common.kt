package com.hamferd.breading.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hamferd.breading.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val longDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
internal val mediumDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
internal val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
internal fun ToolbarIcon(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

/**
 * The row along the bottom of the plan and Bible views: a switch between the two, then
 * arrows that step through days (plan) or chapters (Bible) on either side of [label].
 * A null [onPrevious] or [onNext] disables that arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewBar(
    showingBible: Boolean,
    onSwitchView: () -> Unit,
    previousDescription: String,
    onPrevious: (() -> Unit)?,
    nextDescription: String,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    Column(modifier) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow {
                listOf(
                    Triple(false, R.drawable.ic_calendar, "Reading plan"),
                    Triple(true, R.drawable.ic_menu_book, "Bible"),
                ).forEachIndexed { i, (bible, icon, description) ->
                    SegmentedButton(
                        selected = showingBible == bible,
                        onClick = { if (showingBible != bible) onSwitchView() },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = {},
                        label = { Icon(painterResource(icon), contentDescription = description) },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StepButton(R.drawable.ic_chevron_left, previousDescription, onPrevious)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { label() }
            StepButton(R.drawable.ic_chevron_right, nextDescription, onNext)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickDialog(initial: LocalDate, title: String, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    // DatePicker works in UTC milliseconds.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("Set date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, description: String, onClick: (() -> Unit)?) {
    IconButton(onClick = { onClick?.invoke() }, enabled = onClick != null) {
        Icon(painterResource(icon), contentDescription = description)
    }
}
