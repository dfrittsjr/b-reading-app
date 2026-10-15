package com.hamferd.breading.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class VerseNoteTest {
    private fun chapter(book: String, chapter: Int) = ChapterRef(Bible.findBook(book)!!, chapter)

    @Test
    fun formatsReferences() {
        assertEquals("16", verseRanges(listOf(16)))
        assertEquals("16–18, 20", verseRanges(listOf(20, 16, 17, 18)))
        assertEquals("1, 3, 5–6", verseRanges(listOf(1, 3, 5, 6, 6)))
        assertEquals("John 3:16–17", verseReference(chapter("John", 3), listOf(16, 17)))
        assertEquals("Psalm 23:1", verseReference(chapter("Psalms", 23), listOf(1)))
        assertEquals("Song of Solomon 2:4", verseReference(chapter("Song of Solomon", 2), listOf(4)))
    }

    @Test
    fun encodesAndDecodes() {
        val note = VerseNote("1-a", chapter("1 John", 4), listOf(8, 16), "God is love.\n\tLine \"two\"", Instant.parse("2026-10-14T22:01:02.345Z"))
        assertEquals(note, VerseNote.decode(note.encode()))
    }

    @Test
    fun rejectsNotesThatDontPointAtVerses() {
        val ok = VerseNote.Entry("x", "John", 3, listOf(16), "text", "2026-10-14T00:00:00Z")
        assertEquals("John 3:16", ok.toNote()?.reference)
        assertNull(ok.copy(book = "Hezekiah").toNote())
        assertNull(ok.copy(chapter = 22).toNote())
        assertNull(ok.copy(verses = emptyList()).toNote())
        assertNull(ok.copy(verses = listOf(0, -1)).toNote())
        assertNull(VerseNote.decode("not json"))
    }

    @Test
    fun keepsNoteWithUnreadableTime() {
        val entry = VerseNote.Entry("x", "John", 3, listOf(17, 16, 16), "text", updatedAt = "yesterday")
        val note = entry.toNote()!!
        assertEquals(Instant.EPOCH, note.updatedAt)
        assertEquals(listOf(16, 17), note.verses)
    }
}
