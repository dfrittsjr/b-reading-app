package com.hamferd.breading.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamferd.breading.MainViewModel
import com.hamferd.breading.R
import com.hamferd.breading.data.BibleText
import com.hamferd.breading.data.VerseNote
import java.time.ZoneId

/**
 * Writes or edits a note, with the verses it's on quoted above. Back and Save both keep what
 * was written (a blank new note is dropped, and clearing an old one deletes it); Delete asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditor(
    reference: String,
    quote: AnnotatedString,
    initialText: String,
    isNew: Boolean,
    onDone: (text: String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    BackHandler { onDone(text) }
    LaunchedEffect(Unit) { if (isNew) focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New note" else "Note") },
                navigationIcon = { ToolbarIcon(R.drawable.ic_arrow_back, "Save and close") { onDone(text) } },
                actions = {
                    if (!isNew) ToolbarIcon(R.drawable.ic_delete, "Delete note") { confirmingDelete = true }
                    TextButton(onClick = { onDone(text) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(reference, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                quote,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write a note…") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("Your note on $reference will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The text of the selected verses, numbered, to quote above a note. */
@Composable
internal fun quote(bible: BibleText, selection: VerseSelection): AnnotatedString {
    val numberColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(bible, selection, numberColor, mutedColor) {
        val chapter = bible.chapters[selection.chapterIndex]
        buildAnnotatedString {
            selection.verses.filter { it <= chapter.verses.size }.forEachIndexed { i, verse ->
                if (i > 0) append(" ")
                append(verseText(verse, chapter.verses[verse - 1], numberColor, mutedColor))
            }
        }
    }
}

/** Every note written in the Bible view, most recently edited first. Tapping one shows it in the Bible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleNotesScreen(viewModel: MainViewModel, onBack: () -> Unit, onOpenNote: (VerseNote) -> Unit) {
    val notes by viewModel.verseNotes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bible notes") },
                navigationIcon = { ToolbarIcon(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No Bible notes yet.\nIn the Bible view, tap a verse, then Note.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(notes, key = { it.id }) { note ->
                Card(onClick = { onOpenNote(note) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                note.reference,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                note.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(mediumDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(note.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
