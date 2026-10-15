package com.hamferd.breading.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamferd.breading.MainViewModel
import com.hamferd.breading.R
import com.hamferd.breading.UiState
import com.hamferd.breading.data.DayReadings
import com.hamferd.breading.data.PlanDay
import com.hamferd.breading.data.Reading
import com.hamferd.breading.data.Schedule
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: MainViewModel, onNavigate: (Screen) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPlanMenu by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val ready = state as? UiState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Text(
                            text = ready?.plan?.title ?: "Bible Reading",
                            modifier = Modifier.clickable(enabled = ready != null) { showPlanMenu = true },
                        )
                        DropdownMenu(expanded = showPlanMenu, onDismissRequest = { showPlanMenu = false }) {
                            ready?.plans?.forEach { plan ->
                                DropdownMenuItem(
                                    text = { Text(plan.title) },
                                    onClick = {
                                        showPlanMenu = false
                                        viewModel.selectPlan(plan.id)
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (ready != null) {
                        ToolbarIcon(R.drawable.ic_list, "Choose plan") { showPlanMenu = true }
                        Box {
                            ToolbarIcon(R.drawable.ic_more, "More") { showMoreMenu = true }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                MenuItem("Log other reading", R.drawable.ic_add) {
                                    showMoreMenu = false
                                    onNavigate(Screen.LogReading)
                                }
                                MenuItem("Bible this year", R.drawable.ic_book) {
                                    showMoreMenu = false
                                    onNavigate(Screen.BibleYear)
                                }
                                MenuItem("Plan progress", R.drawable.ic_progress) {
                                    showMoreMenu = false
                                    onNavigate(Screen.PlanProgress)
                                }
                                MenuItem("Back up & restore", R.drawable.ic_backup) {
                                    showMoreMenu = false
                                    onNavigate(Screen.Backup)
                                }
                                MenuItem(
                                    when (ready.plan.schedule) {
                                        Schedule.FROM_START_DATE -> "Set start date"
                                        Schedule.YEARLY -> "Set tracking start"
                                    },
                                    R.drawable.ic_calendar,
                                ) {
                                    showMoreMenu = false
                                    showStartPicker = true
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            when (val s = state) {
                UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                UiState.NoPlans -> CenteredMessage("No reading plans found.\nAdd .txt files to data/plans and rebuild.")
                is UiState.Ready -> ReadingContent(s, viewModel) { onNavigate(Screen.PlanProgress) }
            }
        }
    }

    if (showStartPicker && ready != null) {
        DatePickDialog(
            initial = ready.startDate,
            title = when (ready.plan.schedule) {
                Schedule.FROM_START_DATE -> "Plan start date"
                Schedule.YEARLY -> "Track readings from"
            },
            onDismiss = { showStartPicker = false },
            onConfirm = {
                showStartPicker = false
                viewModel.setStartDate(it)
            },
        )
    }
}

@Composable
private fun ReadingContent(state: UiState.Ready, viewModel: MainViewModel, onOpenProgress: () -> Unit) {
    val onToggle = { reading: Reading -> viewModel.setRead(reading.id, !reading.done) }
    val catchUp = if (state.isToday) state.progress.catchUp else emptyList()

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = if (state.isToday) "Today" else state.date.format(mediumDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(state.date.format(longDate), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            when (val day = state.day) {
                is PlanDay.Reading -> PlanProgressCard(day, state, onOpenProgress)
                is PlanDay.BeforeStart -> Text(
                    "This plan starts on ${day.startDate.format(longDate)}.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                is PlanDay.Finished -> Text(
                    "Plan complete! All ${day.totalDays} days finished.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (state.isToday) {
                val left = catchUp.sumOf { d -> d.readings.count { !it.done } } +
                    (state.readings?.readings?.count { !it.done } ?: 0)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        left == 0 -> "All done for today"
                        catchUp.isEmpty() -> "$left ${plural(left, "reading")} left today"
                        else -> "$left ${plural(left, "reading")} left today, " +
                            "including ${state.progress.behindReadings} to catch up"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (catchUp.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }

            if (catchUp.isNotEmpty()) {
                SectionHeader("Catch up", color = MaterialTheme.colorScheme.error)
                catchUp.forEach { day ->
                    Text(
                        "${day.date.format(shortDate)} · Day ${day.dayNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    day.readings.forEach { ReadingCard(it, overdue = true) { onToggle(it) } }
                }
            }

            state.readings?.let { day ->
                SectionHeader(if (catchUp.isNotEmpty()) "Today's reading" else "Reading")
                day.readings.forEach { ReadingCard(it) { onToggle(it) } }
            }

            SectionHeader("Notes")
            key(state.date) {
                NoteField(state.date, state.note, viewModel::saveNote)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                when (state.plan.schedule) {
                    Schedule.FROM_START_DATE -> "Started ${state.startDate.format(mediumDate)}"
                    Schedule.YEARLY -> "Follows the calendar year (day 1 is January 1). " +
                        "Tracking since ${state.startDate.format(mediumDate)}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.plan.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(R.drawable.ic_chevron_left, "Previous day", viewModel::previousDay)
            TextButton(onClick = viewModel::goToToday, enabled = !state.isToday) { Text("Today") }
            ToolbarIcon(R.drawable.ic_chevron_right, "Next day", viewModel::nextDay)
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color = Color.Unspecified) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ReadingCard(reading: Reading, overdue: Boolean = false, onToggle: () -> Unit) {
    // Unread readings carried over from an earlier day stand out in the error color.
    val behind = overdue && !reading.done
    val colors = when {
        reading.done -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        behind -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> CardDefaults.cardColors()
    }
    Card(onClick = onToggle, colors = colors, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                reading.label?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (behind) LocalContentColor.current else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = reading.passage,
                    style = MaterialTheme.typography.headlineSmall,
                    textDecoration = if (reading.done) TextDecoration.LineThrough else null,
                    color = if (reading.done) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                )
            }
            Checkbox(
                checked = reading.done,
                onCheckedChange = { onToggle() },
                colors = if (behind) {
                    CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.onErrorContainer)
                } else {
                    CheckboxDefaults.colors()
                },
            )
        }
    }
}

/** Compact plan progress on the main screen; tap for the full progress view. */
@Composable
private fun PlanProgressCard(day: PlanDay.Reading, state: UiState.Ready, onClick: () -> Unit) {
    val p = state.progress
    val fraction = if (p.totalReadings == 0) 0f else p.completedReadings / p.totalReadings.toFloat()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Day ${day.dayNumber} of ${day.totalDays}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text("${(fraction * 100).toInt()}% read", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (p.behindReadings > 0) {
                Text(
                    "Behind by ${p.behindReadings} ${plural(p.behindReadings, "reading")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text("On track", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}

/** Edits the note for [date], saving shortly after typing stops and when leaving the day. */
@Composable
private fun NoteField(date: LocalDate, saved: String, onSave: (LocalDate, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(saved) }
    val latestSaved by rememberUpdatedState(saved)

    LaunchedEffect(text) {
        if (text != latestSaved) {
            delay(600)
            onSave(date, text)
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (text != latestSaved) onSave(date, text) }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Thoughts on today's reading…") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun plural(count: Int, word: String) = if (count == 1) word else "${word}s"
