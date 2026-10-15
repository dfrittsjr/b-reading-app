package com.hamferd.breading.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

private val Context.progressDataStore by preferencesDataStore(name = "progress")

data class ProgressData(
    /** Completed plan readings, by plan ID. */
    val completed: Map<String, List<Completion>>,
    /** Daily notes, by calendar date (shared across plans). */
    val notes: Map<LocalDate, String>,
    /** Readings logged from outside any plan, newest first. */
    val otherReadings: List<OtherReading>,
) {
    fun completedIds(planId: String): Set<String> = completed[planId].orEmpty().mapTo(HashSet()) { it.readingId }
}

/** Persists which readings are done, the daily notes, and readings logged elsewhere. */
class ProgressStore(private val context: Context) {
    val data: Flow<ProgressData> = context.progressDataStore.data.map { prefs ->
        val completed = mutableMapOf<String, List<Completion>>()
        val notes = mutableMapOf<LocalDate, String>()
        for ((key, value) in prefs.asMap()) {
            when {
                key.name.startsWith(DONE_PREFIX) ->
                    @Suppress("UNCHECKED_CAST")
                    completed[key.name.removePrefix(DONE_PREFIX)] = (value as Set<String>).map(::decodeCompletion)
                key.name.startsWith(NOTE_PREFIX) -> {
                    val date = runCatching { LocalDate.parse(key.name.removePrefix(NOTE_PREFIX)) }.getOrNull()
                    if (date != null) notes[date] = value as String
                }
            }
        }
        val other = prefs[OTHER_READINGS].orEmpty().mapNotNull(OtherReading::decode)
            .sortedWith(compareByDescending<OtherReading> { it.date }.thenByDescending { it.id })
        ProgressData(completed, notes, other)
    }

    /** Replaces all stored progress with [data] (used when importing a backup). */
    suspend fun replaceAll(data: ProgressData) {
        context.progressDataStore.edit { prefs ->
            prefs.clear()
            for ((planId, completions) in data.completed) {
                prefs[stringSetPreferencesKey(DONE_PREFIX + planId)] =
                    completions.mapTo(HashSet()) { c -> c.date?.let { "${c.readingId}$DATE_SEPARATOR$it" } ?: c.readingId }
            }
            for ((date, text) in data.notes) {
                if (text.isNotBlank()) prefs[stringPreferencesKey(NOTE_PREFIX + date)] = text
            }
            prefs[OTHER_READINGS] = data.otherReadings.mapTo(HashSet()) { it.encode() }
        }
    }

    suspend fun setDone(planId: String, readingId: String, done: Boolean, on: LocalDate) {
        context.progressDataStore.edit { prefs ->
            val key = stringSetPreferencesKey(DONE_PREFIX + planId)
            val others = prefs[key].orEmpty().filterTo(HashSet()) { decodeCompletion(it).readingId != readingId }
            prefs[key] = if (done) others + "$readingId$DATE_SEPARATOR$on" else others
        }
    }

    suspend fun setNote(date: LocalDate, text: String) {
        context.progressDataStore.edit { prefs ->
            val key = stringPreferencesKey(NOTE_PREFIX + date)
            if (text.isBlank()) prefs.remove(key) else prefs[key] = text
        }
    }

    suspend fun addOtherReading(date: LocalDate, passage: String, source: String) {
        // Time-ordered prefix keeps entries on the same date in the order they were added.
        val id = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        context.progressDataStore.edit { prefs ->
            prefs[OTHER_READINGS] = prefs[OTHER_READINGS].orEmpty() + OtherReading(id, date, passage, source).encode()
        }
    }

    suspend fun deleteOtherReading(id: String) {
        context.progressDataStore.edit { prefs ->
            prefs[OTHER_READINGS] = prefs[OTHER_READINGS].orEmpty().filterTo(HashSet()) { OtherReading.decode(it)?.id != id }
        }
    }

    private companion object {
        const val DONE_PREFIX = "done_"
        const val NOTE_PREFIX = "note_"
        const val DATE_SEPARATOR = "|"
        val OTHER_READINGS = stringSetPreferencesKey("other_readings")

        /** Entries are "readingId|yyyy-MM-dd"; entries saved before dates were recorded have no date. */
        fun decodeCompletion(value: String) = Completion(
            readingId = value.substringBefore(DATE_SEPARATOR),
            date = value.substringAfter(DATE_SEPARATOR, "").takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }
}
