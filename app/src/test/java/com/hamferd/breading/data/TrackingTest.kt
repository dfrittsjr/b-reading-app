package com.hamferd.breading.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrackingTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val plan = PlanParser.parse(
        "test",
        (1..10).joinToString("\n") { "Day $it A; Day $it B" },
    )

    @Test
    fun unreadEarlierDaysCarryForwardAsCatchUp() {
        val today = start.plusDays(3) // day 4
        // Day 1 fully read, day 2 half read, day 3 untouched, one reading done ahead on day 6.
        val completed = setOf("1/0", "1/1", "2/0", "6/1")
        val tracked = plan.trackedDays(start, today, completed)
        val progress = PlanProgress.of(tracked, today)

        assertEquals(10, tracked.size)
        assertEquals(listOf(2, 3), progress.catchUp.map { it.dayNumber })
        assertEquals(3, progress.behindReadings)
        assertEquals(4, progress.completedReadings)
        assertEquals(20, progress.totalReadings)
        assertEquals(1, progress.readAhead)
    }

    @Test
    fun completingCatchUpRemovesTheDay() {
        val today = start.plusDays(2)
        val tracked = plan.trackedDays(start, today, setOf("1/0", "1/1", "2/0", "2/1"))
        assertEquals(emptyList<DayReadings>(), PlanProgress.of(tracked, today).catchUp)
    }

    @Test
    fun yearlyPlansTrackFromStartDateAndKeyByYear() {
        val yearly = PlanParser.parse(
            "yearly",
            "schedule: yearly\n" + (1..365).joinToString("\n") { "Day $it" },
        )
        val trackFrom = LocalDate.of(2026, 10, 13)
        val tracked = yearly.trackedDays(trackFrom, LocalDate.of(2026, 10, 15), emptySet())

        assertEquals(LocalDate.of(2026, 10, 13), tracked.first().date)
        assertEquals("2026-286/0", tracked.first().readings.single().id)
        assertEquals(LocalDate.of(2026, 12, 31), tracked.last().date)
        assertEquals(2, PlanProgress.of(tracked, LocalDate.of(2026, 10, 15)).catchUp.size)

        // Next year starts fresh from January 1, with nothing carried over.
        val nextYear = yearly.trackedDays(trackFrom, LocalDate.of(2027, 1, 2), emptySet())
        assertEquals(365, nextYear.size)
        assertEquals("2027-1/0", nextYear.first().readings.single().id)
    }

    @Test
    fun leapDayIsNotCountedTwice() {
        val yearly = PlanParser.parse("yearly", "schedule: yearly\n" + (1..365).joinToString("\n") { "Day $it" })
        val tracked = yearly.trackedDays(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 3, 1), emptySet())
        assertEquals(365, tracked.size)
    }
}
