package com.hamferd.breading.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CoverageTest {
    private fun chapters(passage: String) = PassageParser.chapters(passage)?.map { "${it.book.name} ${it.chapter}" }

    @Test
    fun bibleHas1189Chapters() {
        assertEquals(66, Bible.books.size)
        assertEquals(929, Bible.books.filter { it.isOldTestament }.sumOf { it.chapters })
        assertEquals(260, Bible.books.filterNot { it.isOldTestament }.sumOf { it.chapters })
        assertEquals(1189, Bible.totalChapters)
    }

    @Test
    fun parsesPassageForms() {
        assertEquals(listOf("Genesis 1", "Genesis 2"), chapters("Genesis 1-2"))
        assertEquals(listOf("Exodus 11", "Exodus 12"), chapters("Exodus 11:1-12:21"))
        assertEquals(listOf("Psalms 119"), chapters("Psalm 119:1-24"))
        assertEquals(listOf("Jeremiah 36", "Jeremiah 45"), chapters("Jeremiah 36, 45"))
        assertEquals(listOf("John 3"), chapters("John 3:16, 18"))
        assertEquals(listOf("1 Corinthians 13"), chapters("1 Cor 13"))
        assertEquals(listOf("Song of Solomon 2"), chapters("Song of Songs 2"))
        assertEquals(listOf("Philemon 1"), chapters("Philemon 1"))
    }

    @Test
    fun rejectsInvalidPassages() {
        assertNull(chapters("Jude 2"))
        assertNull(chapters("Hezekiah 1"))
        assertNull(chapters("Genesis"))
        assertNull(chapters("Genesis 5-3"))
    }

    @Test
    fun everyBundledPlanPassageParses() {
        val plans = File("../data/plans").listFiles { f -> f.name.endsWith(".txt") }!!
            .map { PlanParser.parse(it.nameWithoutExtension, it.readText()) }
        assertEquals(3, plans.size)
        for (plan in plans) {
            for (passage in plan.days.flatten()) {
                assert(PassageParser.chapters(passage) != null) { "${plan.id}: can't parse '$passage'" }
            }
        }
        // M'Cheyne covers the whole Bible in a year.
        val mcheyne = plans.single { it.id == "mcheyne" }
        assertEquals(1189, YearCoverage.of(2026, mcheyne.days.flatten()).chaptersRead)
    }

    @Test
    fun coverageCombinesSources() {
        val coverage = YearCoverage.of(2026, listOf("Genesis 1-3", "Genesis 2", "Jude 1", "Romans 8"))
        assertEquals(5, coverage.chaptersRead)
        assertEquals(3, coverage.chaptersRead(oldTestament = true))
        assertEquals(1, coverage.booksFinished) // Jude
    }

    @Test
    fun completedPassageUsesRecordedDateOrSchedule() {
        val plan = PlanParser.parse("p", "Genesis 1; Matthew 1\nGenesis 2; Matthew 2")
        val start = LocalDate.of(2025, 12, 31)
        assertEquals("Matthew 2" to 2026, plan.completedPassage(Completion("2/1", null), start))
        assertEquals("Genesis 1" to 2026, plan.completedPassage(Completion("1/0", LocalDate.of(2026, 3, 1)), start))
        assertNull(plan.completedPassage(Completion("9/0", null), start))

        val yearly = PlanParser.parse("y", "schedule: yearly\nGenesis 1")
        assertEquals("Genesis 1" to 2027, yearly.completedPassage(Completion("2027-1/0", null), start))
    }

    @Test
    fun otherReadingRoundTrips() {
        val reading = OtherReading("1-ab", LocalDate.of(2026, 10, 12), "Romans 8", "Church\twith tab")
        assertEquals(reading.copy(source = "Church with tab"), OtherReading.decode(reading.encode()))
    }
}
