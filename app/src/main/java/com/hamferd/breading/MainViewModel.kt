package com.hamferd.breading

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamferd.breading.data.Backup
import com.hamferd.breading.data.BibleText
import com.hamferd.breading.data.ChapterRef
import com.hamferd.breading.data.Completion
import com.hamferd.breading.data.DayReadings
import com.hamferd.breading.data.OtherReading
import com.hamferd.breading.data.PlanDay
import com.hamferd.breading.data.PlanProgress
import com.hamferd.breading.data.PlanRepository
import com.hamferd.breading.data.ProgressData
import com.hamferd.breading.data.ProgressStore
import com.hamferd.breading.data.ReaderPosition
import com.hamferd.breading.data.ReaderStore
import com.hamferd.breading.data.ReadingPlan
import com.hamferd.breading.data.SettingsStore
import com.hamferd.breading.data.Snapshot
import com.hamferd.breading.data.VerseNote
import com.hamferd.breading.data.merge
import com.hamferd.breading.data.YearCoverage
import com.hamferd.breading.data.completedPassage
import com.hamferd.breading.data.dayFor
import com.hamferd.breading.data.readingsOn
import com.hamferd.breading.data.trackedDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface UiState {
    data object Loading : UiState
    data object NoPlans : UiState
    data class Ready(
        val plans: List<ReadingPlan>,
        val plan: ReadingPlan,
        val startDate: LocalDate,
        val today: LocalDate,
        /** The date being viewed. */
        val date: LocalDate,
        val day: PlanDay,
        /** Readings on [date], or null if the plan has no day there. */
        val readings: DayReadings?,
        /** Every plan day counted toward progress. */
        val trackedDays: List<DayReadings>,
        val progress: PlanProgress,
        /** The note for [date]. */
        val note: String,
        /** Readings logged outside any plan, newest first. */
        val otherReadings: List<OtherReading>,
        /** Chapters read this calendar year across every plan and logged reading. */
        val yearCoverage: YearCoverage,
    ) : UiState {
        val isToday: Boolean get() = date == today
    }
}

data class BackupUiState(
    val busy: Boolean = false,
    /** A backup that was read from a file and is waiting for the user to confirm the import. */
    val pendingImport: Backup? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val progressStore = ProgressStore(app)
    private val readerStore = ReaderStore(app)

    private val plans = flow { emit(PlanRepository(app.assets).loadAll()) }
        .flowOn(Dispatchers.IO)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val today = MutableStateFlow(LocalDate.now())

    /** Days away from today currently being viewed (0 = today). */
    private val dayOffset = MutableStateFlow(0L)

    val state: StateFlow<UiState> = combine(
        plans, settingsStore.settings, progressStore.data, today, dayOffset,
    ) { plans, settings, progressData, today, offset ->
        val plan = plans.firstOrNull { it.id == settings.selectedPlanId } ?: plans.firstOrNull()
            ?: return@combine UiState.NoPlans
        val startDate = settings.startDates[plan.id] ?: today
        val date = today.plusDays(offset)
        val completed = progressData.completedIds(plan.id)
        val tracked = plan.trackedDays(startDate, today, completed)
        UiState.Ready(
            plans = plans,
            plan = plan,
            startDate = startDate,
            today = today,
            date = date,
            day = plan.dayFor(startDate, date),
            readings = plan.readingsOn(startDate, date, completed),
            trackedDays = tracked,
            progress = PlanProgress.of(tracked, today),
            note = progressData.notes[date].orEmpty(),
            otherReadings = progressData.otherReadings,
            yearCoverage = yearCoverage(today.year, plans, settings.startDates, progressData, today),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    init {
        // Make sure the active plan has a persisted start date so "today" keeps advancing.
        viewModelScope.launch {
            val all = plans.first()
            val settings = settingsStore.settings.first()
            val plan = all.firstOrNull { it.id == settings.selectedPlanId } ?: all.firstOrNull()
            if (plan != null) settingsStore.selectPlan(plan.id, today.value)
        }
    }

    /** Called on resume so the screen rolls over to the new day after midnight. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (now != today.value) {
            today.value = now
            dayOffset.value = 0
        }
    }

    fun selectPlan(planId: String) {
        dayOffset.value = 0
        viewModelScope.launch { settingsStore.selectPlan(planId, today.value) }
    }

    fun setStartDate(date: LocalDate) {
        val ready = state.value as? UiState.Ready ?: return
        dayOffset.value = 0
        viewModelScope.launch { settingsStore.setStartDate(ready.plan.id, date) }
    }

    fun setRead(readingId: String, done: Boolean) {
        val ready = state.value as? UiState.Ready ?: return
        viewModelScope.launch { progressStore.setDone(ready.plan.id, readingId, done, LocalDate.now()) }
    }

    fun addOtherReading(date: LocalDate, passage: String, source: String) {
        viewModelScope.launch { progressStore.addOtherReading(date, passage, source) }
    }

    fun deleteOtherReading(id: String) {
        viewModelScope.launch { progressStore.deleteOtherReading(id) }
    }

    fun saveNote(date: LocalDate, text: String) {
        viewModelScope.launch { progressStore.setNote(date, text) }
    }

    private val _backup = MutableStateFlow(BackupUiState())
    val backup: StateFlow<BackupUiState> = _backup

    private suspend fun snapshot() = Snapshot(settingsStore.settings.first(), progressStore.data.first())

    fun exportTo(uri: Uri) {
        _backup.value = BackupUiState(busy = true)
        viewModelScope.launch {
            _backup.value = runCatching {
                val backup = Backup.of(snapshot(), Instant.now().toString())
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                        ?: error("Couldn't open the file.")
                    out.use { it.write(Backup.encode(backup).toByteArray()) }
                }
                BackupUiState(message = "Exported ${describe(backup)}.")
            }.getOrElse { BackupUiState(message = "Export failed: ${it.message}", isError = true) }
        }
    }

    /** Reads a backup file; the import happens after [confirmImport]. */
    fun readBackup(uri: Uri) {
        _backup.value = BackupUiState(busy = true)
        viewModelScope.launch {
            _backup.value = runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() } ?: error("Couldn't open the file.")
                }
                BackupUiState(pendingImport = Backup.decode(text))
            }.getOrElse { BackupUiState(message = "Import failed: ${it.message}", isError = true) }
        }
    }

    fun confirmImport() {
        val backup = _backup.value.pendingImport ?: return
        _backup.value = BackupUiState(busy = true)
        viewModelScope.launch {
            _backup.value = runCatching {
                val merged = merge(snapshot(), backup.toSnapshot())
                settingsStore.replaceAll(merged.settings)
                progressStore.replaceAll(merged.progress)
                dayOffset.value = 0
                BackupUiState(message = "Imported ${describe(backup)}.")
            }.getOrElse { BackupUiState(message = "Import failed: ${it.message}", isError = true) }
        }
    }

    fun cancelImport() { _backup.value = BackupUiState() }

    /** The KJV text, loaded the first time the Bible view opens. */
    val bible: StateFlow<BibleText?> = flow { emit(BibleText.loadKjv(app.assets)) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Where the Bible view was left, or null until [loadReaderPosition] has read it. */
    var readerPosition: ReaderPosition? = null
        private set
    private var savingPosition: Job? = null

    suspend fun loadReaderPosition(): ReaderPosition =
        readerPosition ?: readerStore.position.first().also { readerPosition = it }

    fun setReaderPosition(position: ReaderPosition) {
        if (position == readerPosition) return
        readerPosition = position
        // Save once scrolling settles rather than for every verse that goes by.
        savingPosition?.cancel()
        savingPosition = viewModelScope.launch {
            delay(500)
            readerStore.save(position)
        }
    }

    /** Notes written in the Bible view, most recently edited first. */
    val verseNotes: StateFlow<List<VerseNote>> = progressStore.data.map { it.verseNotes }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Saves a note on [verses] of [chapter], as a new note when [id] is null. Blank [text] deletes it. */
    fun saveVerseNote(id: String?, chapter: ChapterRef, verses: List<Int>, text: String) {
        viewModelScope.launch {
            when {
                text.isNotBlank() -> progressStore.saveVerseNote(
                    VerseNote(id ?: VerseNote.newId(), chapter, verses.sorted(), text, Instant.now()),
                )
                id != null -> progressStore.deleteVerseNote(id)
            }
        }
    }

    fun deleteVerseNote(id: String) {
        viewModelScope.launch { progressStore.deleteVerseNote(id) }
    }

    /** Makes the Bible view open at [note]'s first verse. */
    fun showInBible(note: VerseNote) = setReaderPosition(ReaderPosition(note.chapter, note.verses.first()))

    fun previousDay() = dayOffset.update { it - 1 }
    fun nextDay() = dayOffset.update { it + 1 }
    fun goToToday() { dayOffset.value = 0 }
    fun goToDate(date: LocalDate) { dayOffset.value = ChronoUnit.DAYS.between(today.value, date) }
}

private fun yearCoverage(
    year: Int,
    plans: List<ReadingPlan>,
    startDates: Map<String, LocalDate>,
    progress: ProgressData,
    today: LocalDate,
): YearCoverage {
    val fromPlans = plans.flatMap { plan ->
        val startDate = startDates[plan.id] ?: today
        progress.completed[plan.id].orEmpty().mapNotNull { completion: Completion ->
            plan.completedPassage(completion, startDate)?.takeIf { it.second == year }?.first
        }
    }
    val fromElsewhere = progress.otherReadings.filter { it.date.year == year }.map { it.passage }
    return YearCoverage.of(year, fromPlans + fromElsewhere)
}

internal fun describe(backup: Backup): String =
    "${backup.completedCount} checked-off readings, ${backup.notes.size} day notes, " +
        "${backup.verseNotes.size} Bible notes, and ${backup.otherReadings.size} logged readings"
