package com.hamferd.breading.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/** A note written in the Bible view on one or more verses of a chapter. */
data class VerseNote(
    val id: String,
    val chapter: ChapterRef,
    /** Verse numbers, ascending. */
    val verses: List<Int>,
    val text: String,
    val updatedAt: Instant,
) {
    /** E.g. "John 3:16–18, 20". */
    val reference: String get() = verseReference(chapter, verses)

    fun toEntry() = Entry(id, chapter.book.name, chapter.chapter, verses, text, updatedAt.toString())

    fun encode(): String = json.encodeToString(Entry.serializer(), toEntry())

    /** How a note is stored on the phone and in backup files. Field names are part of the backup format. */
    @Serializable
    data class Entry(
        val id: String,
        val book: String,
        val chapter: Int,
        val verses: List<Int>,
        val text: String,
        val updatedAt: String = "",
    ) {
        /** The note, or null if it doesn't point at real verses. */
        fun toNote(): VerseNote? {
            val b = Bible.findBook(book)?.takeIf { chapter in 1..it.chapters } ?: return null
            val numbers = verses.filter { it >= 1 }.distinct().sorted().ifEmpty { return null }
            val time = runCatching { Instant.parse(updatedAt) }.getOrDefault(Instant.EPOCH)
            return VerseNote(id, ChapterRef(b, chapter), numbers, text, time)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String): VerseNote? =
            runCatching { json.decodeFromString(Entry.serializer(), value) }.getOrNull()?.toNote()

        // Time-ordered prefix, like logged readings.
        fun newId(): String = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
    }
}

/** "John 3:16–18, 20" for those verses of John 3. */
fun verseReference(chapter: ChapterRef, verses: List<Int>): String = "${chapter.label}:${verseRanges(verses)}"

/** "16–18, 20" for verses 16, 17, 18 and 20. */
fun verseRanges(verses: List<Int>): String {
    val ranges = mutableListOf<IntRange>()
    for (verse in verses.sorted().distinct()) {
        val last = ranges.lastOrNull()
        if (last != null && verse == last.last + 1) ranges[ranges.lastIndex] = last.first..verse else ranges += verse..verse
    }
    return ranges.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" }
}
