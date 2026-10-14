package com.hamferd.breading.data

import java.time.LocalDate

/** One passage scheduled on a specific day, with whether it has been read. */
data class Reading(val id: String, val passage: String, val label: String?, val done: Boolean)

/** A plan day as it falls on a calendar [date]. [key] identifies it for completion tracking. */
data class DayReadings(val key: String, val date: LocalDate, val dayNumber: Int, val readings: List<Reading>) {
    val doneCount: Int get() = readings.count { it.done }
    val isComplete: Boolean get() = readings.all { it.done }
}

/**
 * The plan's readings on [date] with their completion state from [completed] (a set of
 * [Reading.id]s), or null if no plan day falls on [date].
 */
fun ReadingPlan.readingsOn(startDate: LocalDate, date: LocalDate, completed: Set<String>): DayReadings? {
    val day = dayFor(startDate, date) as? PlanDay.Reading ?: return null
    // Yearly plans repeat, so their completions are kept per year.
    val key = when (schedule) {
        Schedule.FROM_START_DATE -> "${day.dayNumber}"
        Schedule.YEARLY -> "${date.year}-${day.dayNumber}"
    }
    val readings = day.passages.mapIndexed { i, passage ->
        val id = "$key/$i"
        Reading(id, passage, labels.getOrNull(i)?.takeIf { it.isNotEmpty() }, id in completed)
    }
    return DayReadings(key, date, day.dayNumber, readings)
}

/**
 * The plan days that count toward progress, in order: the whole plan for start-date plans,
 * or from the start date through December 31 of the current year for yearly plans.
 */
fun ReadingPlan.trackedDays(startDate: LocalDate, today: LocalDate, completed: Set<String>): List<DayReadings> {
    val (from, to) = when (schedule) {
        Schedule.FROM_START_DATE -> startDate to startDate.plusDays(days.size - 1L)
        Schedule.YEARLY -> maxOf(startDate, LocalDate.of(today.year, 1, 1)) to LocalDate.of(today.year, 12, 31)
    }
    return generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .mapNotNull { readingsOn(startDate, it, completed) }
        .distinctBy { it.key } // Feb 29 repeats Feb 28 in yearly plans
        .toList()
}

data class PlanProgress(
    val completedReadings: Int,
    val totalReadings: Int,
    /** Earlier days that still have unread readings, oldest first. */
    val catchUp: List<DayReadings>,
    /** Readings already done from days after today. */
    val readAhead: Int,
) {
    val behindReadings: Int get() = catchUp.sumOf { day -> day.readings.count { !it.done } }

    companion object {
        fun of(tracked: List<DayReadings>, today: LocalDate) = PlanProgress(
            completedReadings = tracked.sumOf { it.doneCount },
            totalReadings = tracked.sumOf { it.readings.size },
            catchUp = tracked.filter { it.date < today && !it.isComplete },
            readAhead = tracked.filter { it.date > today }.sumOf { it.doneCount },
        )
    }
}
