package com.hamferd.breading.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BibleTextTest {
    private val kjv by lazy { File("../data/bible/kjv.tsv").useLines { BibleText.parse(it) } }

    private fun chapter(book: String, chapter: Int) = kjv.chapters[kjv.indexOf(ChapterRef(Bible.findBook(book)!!, chapter))]

    @Test
    fun bundledKjvHasEveryChapterAndVerse() {
        assertEquals(1189, kjv.chapters.size)
        assertEquals(31102, kjv.chapters.sumOf { it.verses.size })
        kjv.chapters.forEachIndexed { i, chapter -> assertEquals(i, kjv.indexOf(chapter.ref)) }
        assertEquals(176, chapter("Psalms", 119).verses.size)
        assertEquals(14, chapter("3 John", 1).verses.size)
        assertEquals("In the beginning God created the heaven and the earth.", chapter("Genesis", 1).verses[0])
        assertTrue(chapter("John", 3).verses[15].contains("For God so loved the world"))
        assertTrue(chapter("Revelation", 22).verses.last().startsWith("The grace of our Lord Jesus Christ"))
    }

    @Test
    fun linesRoundTripToPositions() {
        assertEquals(1189 + 31102, kjv.lineCount)
        for (line in 0 until kjv.lineCount) {
            assertEquals(line, kjv.lineOf(kjv.positionAt(line)))
        }
        val john3 = kjv.indexOf(ChapterRef(Bible.findBook("John")!!, 3))
        assertEquals(john3, kjv.chapterAt(kjv.firstLine(john3)))
        assertEquals(ReaderPosition(ChapterRef(Bible.findBook("John")!!, 3), 16), kjv.positionAt(kjv.firstLine(john3) + 16))
        assertEquals(john3 - 1, kjv.chapterAt(kjv.firstLine(john3) - 1))
    }

    @Test
    fun outOfRangeVerseClampsToChapter() {
        val genesis1 = ChapterRef(Bible.books.first(), 1)
        assertEquals(kjv.firstLine(0) + 31, kjv.lineOf(ReaderPosition(genesis1, 99)))
    }

    @Test
    fun rejectsMalformedText() {
        assertThrows(IllegalArgumentException::class.java) { BibleText.parse(sequenceOf("Genesis\t1\t1\tIn the beginning")) }
        assertThrows(IllegalArgumentException::class.java) { BibleText.parse(sequenceOf("Genesis\t1\t2\tAnd the earth")) }
        assertThrows(IllegalArgumentException::class.java) { BibleText.parse(sequenceOf("Hezekiah\t1\t1\tNo such book")) }
        assertThrows(IllegalArgumentException::class.java) { BibleText.parse(sequenceOf("Jude\t2\t1\tJude has one chapter")) }
    }

    @Test
    fun splitsSuppliedWords() {
        assertEquals(
            listOf(TextRun("darkness ", false), TextRun("was", true), TextRun(" upon the deep", false)),
            textRuns("darkness [was] upon the deep"),
        )
        assertEquals(
            listOf(TextRun("the same hath not the Father: ", false), TextRun("but he that acknowledgeth the Son", true), TextRun(".", false)),
            textRuns("the same hath not the Father: [[but] he that acknowledgeth the Son]."),
        )
        assertEquals(listOf(TextRun("Jesus wept.", false)), textRuns("Jesus wept."))
    }
}
