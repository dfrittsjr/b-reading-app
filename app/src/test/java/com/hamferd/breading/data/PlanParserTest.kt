package com.hamferd.breading.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PlanParserTest {
    private val sample = """
        title: Test Plan
        description: Two days only
        # a comment

        Genesis 1-2; Matthew 1
        John 3:16-21
    """.trimIndent()

    @Test
    fun parsesMetadataAndDays() {
        val plan = PlanParser.parse("test", sample)
        assertEquals("Test Plan", plan.title)
        assertEquals("Two days only", plan.description)
        assertEquals(listOf(listOf("Genesis 1-2", "Matthew 1"), listOf("John 3:16-21")), plan.days)
    }

    @Test
    fun titleFallsBackToId() {
        val plan = PlanParser.parse("my-plan", "Psalm 1")
        assertEquals("my-plan", plan.title)
        assertNull(plan.description)
    }

    @Test
    fun dayForCountsFromStartDate() {
        val plan = PlanParser.parse("test", sample)
        val start = LocalDate.of(2026, 10, 13)
        assertEquals(PlanDay.BeforeStart(start), plan.dayFor(start, start.minusDays(1)))
        assertEquals(PlanDay.Reading(1, 2, listOf("Genesis 1-2", "Matthew 1")), plan.dayFor(start, start))
        assertEquals(PlanDay.Reading(2, 2, listOf("John 3:16-21")), plan.dayFor(start, start.plusDays(1)))
        assertEquals(PlanDay.Finished(2), plan.dayFor(start, start.plusDays(2)))
    }

    @Test
    fun yearlyPlanFollowsCalendarAndIgnoresStartDate() {
        val text = "schedule: yearly\nlabels: Family; Secret\n" +
            (1..365).joinToString("\n") { "Day $it A; Day $it B" }
        val plan = PlanParser.parse("yearly", text)
        assertEquals(Schedule.YEARLY, plan.schedule)
        assertEquals(listOf("Family", "Secret"), plan.labels)

        val anyStart = LocalDate.of(2030, 6, 1)
        assertEquals(PlanDay.Reading(1, 365, listOf("Day 1 A", "Day 1 B")), plan.dayFor(anyStart, LocalDate.of(2026, 1, 1)))
        assertEquals(PlanDay.Reading(60, 365, listOf("Day 60 A", "Day 60 B")), plan.dayFor(anyStart, LocalDate.of(2027, 3, 1)))
        // Leap years: Feb 29 repeats Feb 28 and Mar 1 stays day 60.
        assertEquals(PlanDay.Reading(59, 365, listOf("Day 59 A", "Day 59 B")), plan.dayFor(anyStart, LocalDate.of(2028, 2, 29)))
        assertEquals(PlanDay.Reading(60, 365, listOf("Day 60 A", "Day 60 B")), plan.dayFor(anyStart, LocalDate.of(2028, 3, 1)))
        assertEquals(PlanDay.Reading(365, 365, listOf("Day 365 A", "Day 365 B")), plan.dayFor(anyStart, LocalDate.of(2028, 12, 31)))
    }
}
