package com.hamferd.breading.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.progressDataStore by preferencesDataStore(name = "progress")

data class ProgressData(
    /** Completed [Reading.id]s, by plan ID. */
    val completed: Map<String, Set<String>>,
    /** Daily notes, by calendar date (shared across plans). */
    val notes: Map<LocalDate, String>,
)

/** Persists which readings are done and the daily notes. */
class ProgressStore(private val context: Context) {
    val data: Flow<ProgressData> = context.progressDataStore.data.map { prefs ->
        val completed = mutableMapOf<String, Set<String>>()
        val notes = mutableMapOf<LocalDate, String>()
        for ((key, value) in prefs.asMap()) {
            when {
                key.name.startsWith(DONE_PREFIX) ->
                    @Suppress("UNCHECKED_CAST")
                    completed[key.name.removePrefix(DONE_PREFIX)] = value as Set<String>
                key.name.startsWith(NOTE_PREFIX) -> {
                    val date = runCatching { LocalDate.parse(key.name.removePrefix(NOTE_PREFIX)) }.getOrNull()
                    if (date != null) notes[date] = value as String
                }
            }
        }
        ProgressData(completed, notes)
    }

    suspend fun setDone(planId: String, readingId: String, done: Boolean) {
        context.progressDataStore.edit { prefs ->
            val key = stringSetPreferencesKey(DONE_PREFIX + planId)
            val current = prefs[key].orEmpty()
            prefs[key] = if (done) current + readingId else current - readingId
        }
    }

    suspend fun setNote(date: LocalDate, text: String) {
        context.progressDataStore.edit { prefs ->
            val key = stringPreferencesKey(NOTE_PREFIX + date)
            if (text.isBlank()) prefs.remove(key) else prefs[key] = text
        }
    }

    private companion object {
        const val DONE_PREFIX = "done_"
        const val NOTE_PREFIX = "note_"
    }
}
