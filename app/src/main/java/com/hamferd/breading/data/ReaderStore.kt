package com.hamferd.breading.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader")

/** A place in the Bible: a verse of [chapter], or verse 0 for the chapter's heading. */
data class ReaderPosition(val chapter: ChapterRef, val verse: Int)

/** Remembers where the Bible view was left so it reopens there. Not part of backups. */
class ReaderStore(private val context: Context) {
    val position: Flow<ReaderPosition> = context.readerDataStore.data.map { prefs ->
        prefs[POSITION]?.let(::decode) ?: ReaderPosition(ChapterRef(Bible.books.first(), 1), 0)
    }

    suspend fun save(position: ReaderPosition) {
        context.readerDataStore.edit {
            it[POSITION] = "${position.chapter.book.index}:${position.chapter.chapter}:${position.verse}"
        }
    }

    private fun decode(value: String): ReaderPosition? {
        val (book, chapter, verse) = value.split(':').map { it.toIntOrNull() ?: return null }
            .takeIf { it.size == 3 } ?: return null
        val b = Bible.books.getOrNull(book)?.takeIf { chapter in 1..it.chapters } ?: return null
        return ReaderPosition(ChapterRef(b, chapter), verse.coerceAtLeast(0))
    }

    private companion object {
        val POSITION = stringPreferencesKey("position")
    }
}
