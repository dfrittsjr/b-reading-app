package com.hamferd.breading.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamferd.breading.MainViewModel
import com.hamferd.breading.R
import com.hamferd.breading.UiState
import com.hamferd.breading.data.Bible
import com.hamferd.breading.data.Book
import com.hamferd.breading.data.OtherReading
import com.hamferd.breading.data.PassageParser
import java.time.LocalDate

private val sourceSuggestions = listOf("Church", "Bible study", "Small group", "Devotional")

/** Log readings done outside a plan (church, study, etc.) and see what's been logged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogReadingScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state as? UiState.Ready
    var pendingDelete by remember { mutableStateOf<OtherReading?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Other reading") },
                navigationIcon = { ToolbarIcon(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { LogForm(s.today, viewModel::addOtherReading) }
            item {
                Text(
                    "Logged",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                )
                if (s.otherReadings.isEmpty()) {
                    Text(
                        "Nothing logged yet. Readings you log here count toward \"Bible this year\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(s.otherReadings, key = { it.id }) { reading ->
                LoggedRow(reading) { pendingDelete = reading }
                HorizontalDivider()
            }
        }
    }

    pendingDelete?.let { reading ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete reading?") },
            text = { Text("${reading.passage} on ${reading.date.format(mediumDate)}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOtherReading(reading.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogForm(today: LocalDate, onSave: (LocalDate, String, String) -> Unit) {
    var bookIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var chapters by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    var showBookPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val book = bookIndex?.let { Bible.books[it] }
    val date = LocalDate.parse(dateText)
    val passage = book?.let { "${it.name} ${chapters.trim()}" }
    val chaptersValid = passage != null && chapters.isNotBlank() && PassageParser.chapters(passage) != null

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Log something you read", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(onClick = { showBookPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(book?.name ?: "Choose book")
            }

            OutlinedTextField(
                value = chapters,
                onValueChange = {
                    chapters = it
                    savedMessage = null
                },
                label = { Text("Chapters") },
                placeholder = { Text("e.g. 8, 3-5, or 3:16-21") },
                singleLine = true,
                isError = book != null && chapters.isNotBlank() && !chaptersValid,
                supportingText = {
                    if (book != null && chapters.isNotBlank() && !chaptersValid) {
                        Text("${book.name} has ${book.chapters} ${plural(book.chapters, "chapter")}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text("Where (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sourceSuggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { source = suggestion }, label = { Text(suggestion) })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (date == today) "Today" else date.format(mediumDate),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDatePicker = true }) { Text("Change date") }
            }

            Button(
                onClick = {
                    onSave(date, passage!!, source.trim())
                    savedMessage = "Logged $passage"
                    chapters = ""
                },
                enabled = chaptersValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }

            savedMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showBookPicker) {
        BookPickerDialog(
            onDismiss = { showBookPicker = false },
            onPick = {
                bookIndex = it.index
                savedMessage = null
                showBookPicker = false
            },
        )
    }
    if (showDatePicker) {
        DatePickDialog(
            initial = date,
            title = "Date read",
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateText = it.toString()
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun BookPickerDialog(onDismiss: () -> Unit, onPick: (Book) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val books = Bible.books.filter { it.name.contains(filter.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(books, key = { it.index }) { book ->
                        Text(
                            book.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(book) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LoggedRow(reading: OtherReading, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(reading.passage, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(reading.date.format(mediumDate), reading.source).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ToolbarIcon(R.drawable.ic_delete, "Delete", onDelete)
    }
}
