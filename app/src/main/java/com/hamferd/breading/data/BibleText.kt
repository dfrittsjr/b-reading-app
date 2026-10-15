package com.hamferd.breading.data

import android.content.res.AssetManager

/** One chapter of Bible text. `verses[0]` is verse 1. */
class ChapterText(val ref: ChapterRef, val verses: List<String>)

/**
 * The whole Bible in one translation, every chapter in canonical order.
 *
 * For reading, it is laid out as one long list of lines: each chapter's heading followed by
 * its verses. Line [firstLine] of a chapter is its heading, and the line after it is verse 1.
 */
class BibleText(val chapters: List<ChapterText>) {
    private val firstLines = IntArray(chapters.size + 1).also { starts ->
        chapters.forEachIndexed { i, chapter -> starts[i + 1] = starts[i] + 1 + chapter.verses.size }
    }

    private val firstChapterOfBook: List<Int> = Bible.books.runningFold(0) { total, book -> total + book.chapters }

    val lineCount: Int get() = firstLines.last()

    /** Index into [chapters] of [ref]. */
    fun indexOf(ref: ChapterRef): Int = firstChapterOfBook[ref.book.index] + ref.chapter - 1

    /** The line holding the heading of chapter [chapterIndex]. */
    fun firstLine(chapterIndex: Int): Int = firstLines[chapterIndex]

    /** The chapter that [line] belongs to. */
    fun chapterAt(line: Int): Int {
        val found = firstLines.binarySearch(line)
        return if (found >= 0) found else -found - 2
    }

    /** The line showing [position]. */
    fun lineOf(position: ReaderPosition): Int {
        val chapter = indexOf(position.chapter)
        return firstLines[chapter] + position.verse.coerceIn(0, chapters[chapter].verses.size)
    }

    /** The place in the Bible shown by [line]. */
    fun positionAt(line: Int): ReaderPosition {
        val chapter = chapterAt(line)
        return ReaderPosition(chapters[chapter].ref, line - firstLines[chapter])
    }

    companion object {
        private const val KJV_ASSET = "bible/kjv.tsv"

        /** Loads the King James Version bundled from data/bible. */
        fun loadKjv(assets: AssetManager): BibleText = assets.open(KJV_ASSET).bufferedReader().useLines(::parse)

        /**
         * Reads lines of `book<TAB>chapter<TAB>verse<TAB>text` (see data/bible/README.md).
         * Every chapter of every book must be present, with its verses in order.
         */
        fun parse(lines: Sequence<String>): BibleText {
            val booksByName = Bible.books.associateBy { it.name }
            val verses = Bible.books.map { book -> List(book.chapters) { mutableListOf<String>() } }
            lines.forEachIndexed { i, line ->
                if (line.isBlank()) return@forEachIndexed
                val fields = line.split('\t', limit = 4)
                require(fields.size == 4) { "Line ${i + 1}: expected book, chapter, verse and text" }
                val book = requireNotNull(booksByName[fields[0]]) { "Line ${i + 1}: unknown book ${fields[0]}" }
                val chapter = verses[book.index].getOrNull((fields[1].toIntOrNull() ?: 0) - 1)
                requireNotNull(chapter) { "Line ${i + 1}: ${book.name} has no chapter ${fields[1]}" }
                require(fields[2].toIntOrNull() == chapter.size + 1) {
                    "Line ${i + 1}: expected ${book.name} ${fields[1]}:${chapter.size + 1}"
                }
                chapter += fields[3]
            }
            return BibleText(
                Bible.books.flatMap { book ->
                    (1..book.chapters).map { n ->
                        require(verses[book.index][n - 1].isNotEmpty()) { "${book.name} $n is missing" }
                        ChapterText(ChapterRef(book, n), verses[book.index][n - 1])
                    }
                },
            )
        }
    }
}

/** A stretch of verse text; [supplied] marks words the translators added (italics in the KJV). */
data class TextRun(val text: String, val supplied: Boolean)

/** Splits verse text at its `[supplied]` brackets, which can nest. The brackets are dropped. */
fun textRuns(verse: String): List<TextRun> {
    val runs = mutableListOf<TextRun>()
    val current = StringBuilder()
    var depth = 0
    fun flush() {
        if (current.isNotEmpty()) runs += TextRun(current.toString(), supplied = depth > 0)
        current.clear()
    }
    for (c in verse) {
        when (c) {
            '[' -> { if (depth == 0) flush(); depth++ }
            ']' -> { if (depth == 1) flush(); depth = (depth - 1).coerceAtLeast(0) }
            else -> current.append(c)
        }
    }
    flush()
    return runs
}
