package com.hamferd.breading.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamferd.breading.MainViewModel
import com.hamferd.breading.R
import com.hamferd.breading.UiState
import com.hamferd.breading.data.BookCoverage
import com.hamferd.breading.data.YearCoverage

/** How much of the whole Bible has been read this year, from plans and logged readings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleYearScreen(viewModel: MainViewModel, onBack: () -> Unit, onLogReading: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state as? UiState.Ready
    var expandedBook by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.let { "Bible in ${it.today.year}" } ?: "Bible this year") },
                navigationIcon = { ToolbarIcon(R.drawable.ic_arrow_back, "Back", onBack) },
                actions = { ToolbarIcon(R.drawable.ic_add, "Log other reading", onLogReading) },
            )
        },
    ) { padding ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val coverage = s.yearCoverage
        val (oldTestament, newTestament) = coverage.books.partition { it.book.isOldTestament }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { CoverageSummary(coverage) }
            item { GroupHeader("Old Testament") }
            items(oldTestament, key = { it.book.index }) { book ->
                BookRow(book, expanded = expandedBook == book.book.index) {
                    expandedBook = if (expandedBook == book.book.index) null else book.book.index
                }
            }
            item { GroupHeader("New Testament") }
            items(newTestament, key = { it.book.index }) { book ->
                BookRow(book, expanded = expandedBook == book.book.index) {
                    expandedBook = if (expandedBook == book.book.index) null else book.book.index
                }
            }
            item {
                Text(
                    "Counts plan readings checked off this year and readings you logged. " +
                        "Reading any part of a chapter counts as that chapter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun CoverageSummary(coverage: YearCoverage) {
    val fraction = coverage.chaptersRead / coverage.totalChapters.toFloat()
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(percent(fraction), style = MaterialTheme.typography.displaySmall)
            Text(
                "${coverage.chaptersRead} of ${coverage.totalChapters} chapters read",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("Old Testament", "${coverage.chaptersRead(true)}/${coverage.totalChapters(true)}")
                Stat("New Testament", "${coverage.chaptersRead(false)}/${coverage.totalChapters(false)}")
                Stat("Books finished", "${coverage.booksFinished}/${coverage.books.size}")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookRow(coverage: BookCoverage, expanded: Boolean, onClick: () -> Unit) {
    val done = coverage.chaptersRead.size == coverage.book.chapters
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(coverage.book.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (done) "✓ ${coverage.book.chapters}" else "${coverage.chaptersRead.size}/${coverage.book.chapters}",
                style = MaterialTheme.typography.labelLarge,
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { coverage.fraction }, modifier = Modifier.fillMaxWidth())

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (chapter in 1..coverage.book.chapters) {
                    val read = chapter in coverage.chaptersRead
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$chapter",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (read) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun percent(fraction: Float): String {
    val value = fraction * 100
    return if (value > 0f && value < 1f) "<1%" else "${value.toInt()}%"
}
