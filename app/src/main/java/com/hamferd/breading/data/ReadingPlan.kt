package com.hamferd.breading.data

import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/**
 * A reading plan loaded from a file in data/plans. Each entry in [days] is the list of
 * passages to read on that day (e.g. ["Genesis 1-2", "Matthew 1"]). [labels] optionally
 * names each passage slot (e.g. M'Cheyne's "Family" and "Secret" readings).
 */
data class ReadingPlan(
    val id: String,
    val title: String,
    val description: String?,
    val days: List<List<String>>,
    val schedule: Schedule = Schedule.FROM_START_DATE,
    val labels: List<String> = emptyList(),
)

enum class Schedule {
    /** Day 1 is the start date the user picks. */
    FROM_START_DATE,

    /** Day 1 is January 1 every year; the start date is ignored. */
    YEARLY,
}

sealed interface PlanDay {
    data class BeforeStart(val startDate: LocalDate) : PlanDay
    data class Reading(val dayNumber: Int, val totalDays: Int, val passages: List<String>) : PlanDay
    data class Finished(val totalDays: Int) : PlanDay
}

fun ReadingPlan.dayFor(startDate: LocalDate, date: LocalDate): PlanDay {
    val index = when (schedule) {
        Schedule.FROM_START_DATE -> ChronoUnit.DAYS.between(startDate, date)
        Schedule.YEARLY -> yearlyIndex(date).toLong()
    }
    return when {
        index < 0 -> PlanDay.BeforeStart(startDate)
        index >= days.size -> PlanDay.Finished(days.size)
        else -> PlanDay.Reading(index.toInt() + 1, days.size, days[index.toInt()])
    }
}

/** Zero-based day of a 365-day year. Feb 29 repeats Feb 28 so leap years stay aligned. */
private fun yearlyIndex(date: LocalDate): Int {
    val day = if (date.month == Month.FEBRUARY && date.dayOfMonth == 29) 28 else date.dayOfMonth
    return LocalDate.of(NON_LEAP_YEAR, date.month, day).dayOfYear - 1
}

private const val NON_LEAP_YEAR = 2025
