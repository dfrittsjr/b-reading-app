package com.hamferd.breading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamferd.breading.MainViewModel
import com.hamferd.breading.R
import com.hamferd.breading.UiState
import com.hamferd.breading.data.DayReadings
import com.hamferd.breading.data.PlanDay
import com.hamferd.breading.data.Schedule
import com.hamferd.breading.data.dayFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val rowDate = DateTimeFormatter.ofPattern("MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: MainViewModel, onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                navigationIcon = { ToolbarIcon(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        val s = state as? UiState.Ready
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Open with today's row near the top (index + 1 accounts for the summary item).
        val todayIndex = s.trackedDays.indexOfFirst { it.date == s.today }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (todayIndex > 2) todayIndex - 1 else 0)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Summary(s) }
            items(s.trackedDays, key = { it.key }) { day ->
                DayRow(day, s.today) { onOpenDay(day.date) }
            }
        }
    }
}

@Composable
private fun Summary(s: UiState.Ready) {
    val p = s.progress
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(s.plan.title, style = MaterialTheme.typography.titleLarge)
            (s.plan.dayFor(s.startDate, s.today) as? PlanDay.Reading)?.let {
                Text("Day ${it.dayNumber} of ${it.totalDays}", style = MaterialTheme.typography.bodyMedium)
            }
            if (s.plan.schedule == Schedule.YEARLY) {
                Text(
                    "Tracking ${s.today.year} since ${s.trackedDays.firstOrNull()?.date?.format(mediumDate) ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            val fraction = if (p.totalReadings == 0) 0f else p.completedReadings / p.totalReadings.toFloat()
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
            Text(
                "${p.completedReadings} of ${p.totalReadings} readings done",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            if (p.behindReadings > 0) {
                Text(
                    "Behind by ${p.behindReadings} ${plural(p.behindReadings, "reading")} " +
                        "from ${p.catchUp.size} ${plural(p.catchUp.size, "day")}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text("On track", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (p.readAhead > 0) {
                Text(
                    "${p.readAhead} ${plural(p.readAhead, "reading")} read ahead",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayRow(day: DayReadings, today: LocalDate, onClick: () -> Unit) {
    val isToday = day.date == today
    val missed = day.date < today && !day.isComplete
    val statusColor = when {
        day.isComplete -> MaterialTheme.colorScheme.primary
        missed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isToday) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp)) {
            Text(
                if (isToday) "Today" else day.date.format(rowDate),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else null,
            )
            Text(
                "Day ${day.dayNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            day.readings.joinToString(" · ") { it.passage },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            when {
                day.isComplete -> "✓"
                else -> "${day.doneCount}/${day.readings.size}"
            },
            style = MaterialTheme.typography.titleMedium,
            color = statusColor,
        )
    }
}
