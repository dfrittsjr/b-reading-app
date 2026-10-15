package com.hamferd.breading.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Everything the app stores about you: plan settings and progress, including notes. */
data class Snapshot(val settings: Settings, val progress: ProgressData)

/** The JSON file written by "Export". Field names are part of the file format; don't rename them. */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: String,
    val selectedPlanId: String? = null,
    val startDates: Map<String, String> = emptyMap(),
    val completed: Map<String, List<CompletedEntry>> = emptyMap(),
    val notes: Map<String, String> = emptyMap(),
    val otherReadings: List<OtherReadingEntry> = emptyList(),
    val verseNotes: List<VerseNote.Entry> = emptyList(),
) {
    @Serializable
    data class CompletedEntry(@SerialName("id") val readingId: String, val date: String? = null)

    @Serializable
    data class OtherReadingEntry(val id: String, val date: String, val passage: String, val source: String = "")

    val completedCount: Int get() = completed.values.sumOf { it.size }

    fun toSnapshot() = Snapshot(
        settings = Settings(
            selectedPlanId = selectedPlanId,
            startDates = startDates.mapNotNull { (plan, date) -> parseDate(date)?.let { plan to it } }.toMap(),
        ),
        progress = ProgressData(
            completed = completed.mapValues { (_, entries) ->
                entries.map { Completion(it.readingId, it.date?.let(::parseDate)) }
            },
            notes = notes.mapNotNull { (date, text) -> parseDate(date)?.let { it to text } }.toMap(),
            otherReadings = otherReadings.mapNotNull { e ->
                parseDate(e.date)?.let { OtherReading(e.id, it, e.passage, e.source) }
            },
            verseNotes = verseNotes.mapNotNull { it.toNote() },
        ),
    )

    companion object {
        const val FORMAT = "b-reading-app backup"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun of(snapshot: Snapshot, exportedAt: String) = Backup(
            exportedAt = exportedAt,
            selectedPlanId = snapshot.settings.selectedPlanId,
            startDates = snapshot.settings.startDates.mapValues { it.value.toString() },
            completed = snapshot.progress.completed.mapValues { (_, list) ->
                list.map { CompletedEntry(it.readingId, it.date?.toString()) }
            },
            notes = snapshot.progress.notes.mapKeys { it.key.toString() },
            otherReadings = snapshot.progress.otherReadings.map {
                OtherReadingEntry(it.id, it.date.toString(), it.passage, it.source)
            },
            verseNotes = snapshot.progress.verseNotes.map { it.toEntry() },
        )

        fun encode(backup: Backup): String = json.encodeToString(serializer(), backup)

        /** Throws [IllegalArgumentException] if [text] isn't a backup from this app. */
        fun decode(text: String): Backup {
            val backup = runCatching { json.decodeFromString(serializer(), text) }
                .getOrElse { throw IllegalArgumentException("This file isn't a Bible Reading backup.") }
            require(backup.format == FORMAT) { "This file isn't a Bible Reading backup." }
            require(backup.version <= 1) { "This backup is from a newer version of the app. Update the app first." }
            return backup
        }

        private fun parseDate(value: String) = runCatching { LocalDate.parse(value) }.getOrNull()
    }
}

/**
 * Merges an imported backup into what's on the phone; nothing on the phone is lost.
 * Checked-off readings and logged readings are combined. The backup's plan choice and start
 * dates win, since restoring onto a fresh install should bring them back. A note on the phone
 * is kept if it's not blank, otherwise the backup's note is used. Bible notes are combined, and
 * when both have the same note, the more recently edited version wins.
 */
fun merge(local: Snapshot, imported: Snapshot): Snapshot {
    val completed = (local.progress.completed.keys + imported.progress.completed.keys).associateWith { plan ->
        val mine = local.progress.completed[plan].orEmpty()
        val theirs = imported.progress.completed[plan].orEmpty()
        (mine + theirs)
            .groupBy { it.readingId }
            .map { (id, entries) -> Completion(id, entries.firstNotNullOfOrNull { it.date }) }
    }
    val notes = imported.progress.notes + local.progress.notes.filterValues { it.isNotBlank() }
    val other = (local.progress.otherReadings + imported.progress.otherReadings).distinctBy { it.id }
    val verseNotes = (local.progress.verseNotes + imported.progress.verseNotes)
        .groupBy { it.id }
        .map { (_, versions) -> versions.maxBy { it.updatedAt } } // the phone's comes first, so it wins ties
        .sortedByDescending { it.updatedAt }

    return Snapshot(
        settings = Settings(
            selectedPlanId = imported.settings.selectedPlanId ?: local.settings.selectedPlanId,
            startDates = local.settings.startDates + imported.settings.startDates,
        ),
        progress = ProgressData(completed, notes, other, verseNotes),
    )
}
