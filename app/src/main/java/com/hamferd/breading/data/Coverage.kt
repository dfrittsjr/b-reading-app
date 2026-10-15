package com.hamferd.breading.data

import java.time.LocalDate

data class BookCoverage(val book: Book, val chaptersRead: Set<Int>) {
    val fraction: Float get() = chaptersRead.size / book.chapters.toFloat()
}

/** How much of the Bible was read in [year], by chapter. */
data class YearCoverage(val year: Int, val books: List<BookCoverage>) {
    val chaptersRead: Int get() = books.sumOf { it.chaptersRead.size }
    val totalChapters: Int get() = Bible.totalChapters
    val booksFinished: Int get() = books.count { it.chaptersRead.size == it.book.chapters }

    fun chaptersRead(oldTestament: Boolean) =
        books.filter { it.book.isOldTestament == oldTestament }.sumOf { it.chaptersRead.size }

    fun totalChapters(oldTestament: Boolean) =
        books.filter { it.book.isOldTestament == oldTestament }.sumOf { it.book.chapters }

    companion object {
        /** [passagesRead] is every passage read during [year], from any source. */
        fun of(year: Int, passagesRead: List<String>): YearCoverage {
            val read = passagesRead.flatMap { PassageParser.chapters(it).orEmpty() }
                .groupBy({ it.book.index }, { it.chapter })
            return YearCoverage(year, Bible.books.map { BookCoverage(it, read[it.index].orEmpty().toSet()) })
        }
    }
}

/** A completed plan reading and the date it was checked off (null for older entries). */
data class Completion(val readingId: String, val date: LocalDate?)

/**
 * The passage and year for a completed reading in this plan, or null if [readingId] doesn't
 * match the plan (e.g. the plan file changed). Without a recorded date, the scheduled date is used.
 */
fun ReadingPlan.completedPassage(completion: Completion, startDate: LocalDate): Pair<String, Int>? {
    val key = completion.readingId.substringBefore('/')
    val index = completion.readingId.substringAfter('/').toIntOrNull() ?: return null
    val (scheduledYear, dayNumber) = when (schedule) {
        Schedule.YEARLY -> key.substringBefore('-').toIntOrNull() to key.substringAfter('-').toIntOrNull()
        Schedule.FROM_START_DATE -> key.toIntOrNull()?.let { startDate.plusDays(it - 1L).year to it } ?: (null to null)
    }
    val passage = dayNumber?.let { days.getOrNull(it - 1)?.getOrNull(index) } ?: return null
    val year = completion.date?.year ?: scheduledYear ?: return null
    return passage to year
}
