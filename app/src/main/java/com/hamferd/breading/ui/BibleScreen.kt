package com.hamferd.breading.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamferd.breading.MainViewModel
import com.hamferd.breading.R
import com.hamferd.breading.data.Bible
import com.hamferd.breading.data.BibleText
import com.hamferd.breading.data.Book
import com.hamferd.breading.data.ChapterRef
import com.hamferd.breading.data.ReaderPosition
import com.hamferd.breading.data.VerseNote
import com.hamferd.breading.data.verseReference
import com.hamferd.breading.data.textRuns
import kotlinx.coroutines.launch

/**
 * The KJV as one continuous scroll from Genesis to Revelation, like the ESV app: the bottom
 * bar shows the chapter being read, and tapping it opens a book and chapter picker. Tapping
 * verses selects them for a note, as in the ESV and YouVersion apps.
 */
@Composable
fun BibleScreen(viewModel: MainViewModel, onShowPlan: () -> Unit, onOpenNotes: () -> Unit) {
    val bible by viewModel.bible.collectAsStateWithLifecycle()
    val notes by viewModel.verseNotes.collectAsStateWithLifecycle()
    val start by produceState(viewModel.readerPosition) {
        if (value == null) value = viewModel.loadReaderPosition()
    }
    val text = bible
    val from = start
    if (text == null || from == null) {
        Scaffold(bottomBar = { ReaderBar(onShowPlan, onPrevious = null, onNext = null) {} }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    } else {
        BibleReader(
            bible = text,
            start = from,
            notes = notes,
            onPositionChange = viewModel::setReaderPosition,
            onSaveNote = viewModel::saveVerseNote,
            onDeleteNote = viewModel::deleteVerseNote,
            onShowPlan = onShowPlan,
            onOpenNotes = onOpenNotes,
        )
    }
}

/** Verses picked in one chapter, by tapping them. */
internal data class VerseSelection(val chapterIndex: Int, val verses: List<Int>)

private val selectionSaver = Saver<VerseSelection?, Any>(
    save = { it?.let { selection -> arrayListOf(selection.chapterIndex, ArrayList(selection.verses)) } },
    restore = { saved ->
        val (chapterIndex, verses) = saved as List<*>
        VerseSelection(chapterIndex as Int, (verses as List<*>).map { it as Int })
    },
)

@Composable
private fun BibleReader(
    bible: BibleText,
    start: ReaderPosition,
    notes: List<VerseNote>,
    onPositionChange: (ReaderPosition) -> Unit,
    onSaveNote: (id: String?, chapter: ChapterRef, verses: List<Int>, text: String) -> Unit,
    onDeleteNote: (id: String) -> Unit,
    onShowPlan: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = bible.lineOf(start))
    val scope = rememberCoroutineScope()
    val chapter by remember(bible) { derivedStateOf { bible.chapterAt(listState.firstVisibleItemIndex) } }
    var picking by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<VerseSelection?>(null) }
    // The note editor is open on an existing note, or on verses for a new one.
    var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var newNote by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<VerseSelection?>(null) }
    fun goTo(chapterIndex: Int) {
        scope.launch { listState.scrollToItem(bible.firstLine(chapterIndex)) }
    }

    // Tapping a verse adds it to the selection or takes it out; a verse in another chapter starts over.
    fun toggle(chapterIndex: Int, verse: Int) {
        val current = selection
        selection = when {
            current == null || current.chapterIndex != chapterIndex -> VerseSelection(chapterIndex, listOf(verse))
            verse in current.verses -> (current.verses - verse).takeIf { it.isNotEmpty() }?.let { current.copy(verses = it) }
            else -> current.copy(verses = (current.verses + verse).sorted())
        }
    }
    BackHandler(enabled = selection != null) { selection = null }

    // Each note's icon sits at the end of its last verse.
    val notesByLine = remember(bible, notes) {
        notes.groupBy { bible.lineOf(ReaderPosition(it.chapter, it.verses.last())) }
    }

    val latestOnPositionChange by rememberUpdatedState(onPositionChange)
    LaunchedEffect(bible, listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { latestOnPositionChange(bible.positionAt(it)) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                val selected = selection
                if (selected != null) {
                    SelectionBar(
                        reference = verseReference(bible.chapters[selected.chapterIndex].ref, selected.verses),
                        onClear = { selection = null },
                        onNote = {
                            newNote = selected
                            selection = null
                        },
                    )
                } else {
                    ReaderBar(
                        onShowPlan = onShowPlan,
                        onPrevious = if (chapter > 0) ({ goTo(chapter - 1) }) else null,
                        onNext = if (chapter < bible.chapters.lastIndex) ({ goTo(chapter + 1) }) else null,
                    ) {
                        TextButton(onClick = { picking = true }) {
                            Text(bible.chapters[chapter].ref.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = "Choose book and chapter")
                        }
                    }
                }
            },
        ) { padding ->
            val readingStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                lineHeight = 30.sp,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                items(
                    count = bible.lineCount,
                    contentType = { line -> if (line == bible.firstLine(bible.chapterAt(line))) 0 else 1 },
                ) { line ->
                    val index = bible.chapterAt(line)
                    val text = bible.chapters[index]
                    val verse = line - bible.firstLine(index)
                    if (verse == 0) {
                        ChapterHeading(text.ref)
                    } else {
                        Verse(
                            number = verse,
                            text = text.verses[verse - 1],
                            style = readingStyle,
                            selected = selection?.let { it.chapterIndex == index && verse in it.verses } == true,
                            notes = notesByLine[line].orEmpty(),
                            onClick = { toggle(index, verse) },
                            onOpenNote = { editingNoteId = it.id },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = picking,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            PassagePicker(
                current = bible.chapters[chapter].ref,
                onPick = { ref ->
                    picking = false
                    goTo(bible.indexOf(ref))
                },
                onClose = { picking = false },
                onOpenNotes = onOpenNotes,
            )
        }

        val editing = editingNoteId?.let { id -> notes.firstOrNull { it.id == id } }
        val target = editing?.let { VerseSelection(bible.indexOf(it.chapter), it.verses) } ?: newNote
        if (target != null) {
            val ref = bible.chapters[target.chapterIndex].ref
            key(editing?.id, target) {
                NoteEditor(
                    reference = verseReference(ref, target.verses),
                    quote = quote(bible, target),
                    initialText = editing?.text.orEmpty(),
                    isNew = editing == null,
                    onDone = { text ->
                        if (text != editing?.text.orEmpty()) onSaveNote(editing?.id, ref, target.verses, text)
                        editingNoteId = null
                        newNote = null
                    },
                    onDelete = {
                        editing?.let { onDeleteNote(it.id) }
                        editingNoteId = null
                    },
                )
            }
        }
    }
}

/** Shown instead of the bottom bar while verses are selected. */
@Composable
private fun SelectionBar(reference: String, onClear: () -> Unit, onNote: () -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(R.drawable.ic_close, "Clear selection", onClear)
            Text(
                reference,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            FilledTonalButton(onClick = onNote) {
                Icon(painterResource(R.drawable.ic_edit_note), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Note")
            }
        }
    }
}

@Composable
private fun ReaderBar(
    onShowPlan: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    label: @Composable () -> Unit,
) {
    ViewBar(
        showingBible = true,
        onSwitchView = onShowPlan,
        previousDescription = "Previous chapter",
        onPrevious = onPrevious,
        nextDescription = "Next chapter",
        onNext = onNext,
        modifier = Modifier.navigationBarsPadding(),
        label = label,
    )
}

/** A chapter's large number, with the book's name above its first chapter. */
@Composable
private fun ChapterHeading(ref: ChapterRef) {
    Column(Modifier.fillMaxWidth().padding(top = if (ref.chapter == 1) 48.dp else 28.dp, bottom = 4.dp)) {
        if (ref.chapter == 1) {
            Text(
                ref.book.name,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).semantics { heading() },
            )
        }
        Text(
            "${ref.chapter}",
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics {
                heading()
                contentDescription = ref.label
            },
        )
    }
}

/**
 * One verse: a small raised verse number, then the text with supplied words in italics, and an
 * icon for each note that ends here. Tapping the verse selects it.
 */
@Composable
private fun Verse(
    number: Int,
    text: String,
    style: TextStyle,
    selected: Boolean,
    notes: List<VerseNote>,
    onClick: () -> Unit,
    onOpenNote: (VerseNote) -> Unit,
) {
    val numberColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    val annotated = remember(number, text, notes, numberColor, mutedColor) {
        buildAnnotatedString {
            append(verseText(number, text, numberColor, mutedColor))
            notes.forEach {
                append('\u00A0')
                appendInlineContent(it.id, "[note]")
            }
        }
    }
    val inlineContent = notes.associate { note ->
        note.id to InlineTextContent(Placeholder(1.3.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(
                painterResource(R.drawable.ic_edit_note),
                contentDescription = "Note on ${note.reference}",
                tint = numberColor,
                modifier = Modifier.fillMaxSize().clickable(onClickLabel = "Open note") { onOpenNote(note) },
            )
        }
    }
    Text(
        annotated,
        style = style,
        inlineContent = inlineContent,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (selected) {
                    // A little wider than the text, so the highlight doesn't crowd it.
                    val bleed = 8.dp.toPx()
                    drawRoundRect(
                        selectedColor,
                        topLeft = Offset(-bleed, 0f),
                        size = Size(size.width + 2 * bleed, size.height),
                        cornerRadius = CornerRadius(bleed),
                    )
                }
            }
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = if (selected) "Deselect verse" else "Select verse",
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    )
}

/** A verse number raised before the verse, which shows supplied words in italics. */
internal fun verseText(number: Int, text: String, numberColor: Color, mutedColor: Color): AnnotatedString =
    buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = numberColor,
                fontSize = 0.6.em,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                baselineShift = BaselineShift.Superscript,
            ),
        ) { append("$number") }
        append('\u00A0')
        var body = text
        if (body.startsWith(PILCROW)) {
            withStyle(SpanStyle(color = mutedColor)) { append(PILCROW) }
            body = body.removePrefix(PILCROW)
        }
        for (run in textRuns(body)) {
            if (run.supplied) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(run.text) } else append(run.text)
        }
    }

/** Full-screen book and chapter chooser: testament tabs, then books, then chapter numbers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassagePicker(
    current: ChapterRef,
    onPick: (ChapterRef) -> Unit,
    onClose: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    var newTestament by rememberSaveable { mutableStateOf(!current.book.isOldTestament) }
    var chosenBook by rememberSaveable { mutableStateOf<Int?>(null) }
    val book = chosenBook?.let { Bible.books[it] }
    BackHandler { if (book != null) chosenBook = null else onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.name ?: "Books") },
                navigationIcon = {
                    if (book == null) {
                        ToolbarIcon(R.drawable.ic_close, "Close", onClick = onClose)
                    } else {
                        ToolbarIcon(R.drawable.ic_arrow_back, "Back to books") { chosenBook = null }
                    }
                },
                actions = { ToolbarIcon(R.drawable.ic_edit_note, "Bible notes", onOpenNotes) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            PrimaryTabRow(selectedTabIndex = if (newTestament) 1 else 0) {
                listOf("Old Testament", "New Testament").forEachIndexed { i, title ->
                    Tab(
                        selected = newTestament == (i == 1),
                        onClick = {
                            newTestament = i == 1
                            chosenBook = null
                        },
                        text = { Text(title) },
                    )
                }
            }
            if (book == null) {
                key(newTestament) {
                    BookList(
                        books = Bible.books.filter { it.isOldTestament != newTestament },
                        current = current,
                        onChoose = { b -> if (b.chapters == 1) onPick(ChapterRef(b, 1)) else chosenBook = b.index },
                    )
                }
            } else {
                ChapterGrid(book, current, onPick)
            }
        }
    }
}

@Composable
private fun BookList(books: List<Book>, current: ChapterRef, onChoose: (Book) -> Unit) {
    // Open with the current book in view, a few rows down from the top.
    val currentRow = books.indexOfFirst { it.index == current.book.index }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentRow - 3).coerceAtLeast(0))
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(books, key = { it.index }) { book ->
            val isCurrent = book.index == current.book.index
            ListItem(
                headlineContent = {
                    Text(
                        book.name,
                        fontWeight = if (isCurrent) FontWeight.Bold else null,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                },
                modifier = Modifier.clickable { onChoose(book) },
            )
        }
    }
}

@Composable
private fun ChapterGrid(book: Book, current: ChapterRef, onPick: (ChapterRef) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 56.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(book.chapters) { i ->
            val chapter = i + 1
            val isCurrent = book.index == current.book.index && chapter == current.chapter
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onPick(ChapterRef(book, chapter)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$chapter",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private const val PILCROW = "¶"
