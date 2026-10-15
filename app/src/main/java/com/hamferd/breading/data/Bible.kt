package com.hamferd.breading.data

data class Book(val index: Int, val name: String, val chapters: Int, val aliases: List<String> = emptyList()) {
    val isOldTestament: Boolean get() = index < OLD_TESTAMENT_BOOKS
}

/** A single chapter of a book, e.g. Romans 8. */
data class ChapterRef(val book: Book, val chapter: Int)

private const val OLD_TESTAMENT_BOOKS = 39

object Bible {
    val books: List<Book> = listOf(
        "Genesis" to 50, "Exodus" to 40, "Leviticus" to 27, "Numbers" to 36, "Deuteronomy" to 34,
        "Joshua" to 24, "Judges" to 21, "Ruth" to 4, "1 Samuel" to 31, "2 Samuel" to 24,
        "1 Kings" to 22, "2 Kings" to 25, "1 Chronicles" to 29, "2 Chronicles" to 36, "Ezra" to 10,
        "Nehemiah" to 13, "Esther" to 10, "Job" to 42, "Psalms" to 150, "Proverbs" to 31,
        "Ecclesiastes" to 12, "Song of Solomon" to 8, "Isaiah" to 66, "Jeremiah" to 52,
        "Lamentations" to 5, "Ezekiel" to 48, "Daniel" to 12, "Hosea" to 14, "Joel" to 3,
        "Amos" to 9, "Obadiah" to 1, "Jonah" to 4, "Micah" to 7, "Nahum" to 3, "Habakkuk" to 3,
        "Zephaniah" to 3, "Haggai" to 2, "Zechariah" to 14, "Malachi" to 4,
        "Matthew" to 28, "Mark" to 16, "Luke" to 24, "John" to 21, "Acts" to 28, "Romans" to 16,
        "1 Corinthians" to 16, "2 Corinthians" to 13, "Galatians" to 6, "Ephesians" to 6,
        "Philippians" to 4, "Colossians" to 4, "1 Thessalonians" to 5, "2 Thessalonians" to 3,
        "1 Timothy" to 6, "2 Timothy" to 4, "Titus" to 3, "Philemon" to 1, "Hebrews" to 13,
        "James" to 5, "1 Peter" to 5, "2 Peter" to 3, "1 John" to 5, "2 John" to 1, "3 John" to 1,
        "Jude" to 1, "Revelation" to 22,
    ).mapIndexed { i, (name, chapters) -> Book(i, name, chapters, aliasesFor(name)) }

    val totalChapters: Int = books.sumOf { it.chapters }

    private val byName: Map<String, Book> = buildMap {
        for (book in books) {
            put(normalize(book.name), book)
            book.aliases.forEach { put(normalize(it), book) }
        }
    }

    /** Finds a book by full name, alias, or unambiguous abbreviation ("Gen", "1 Cor"). */
    fun findBook(name: String): Book? {
        val key = normalize(name)
        if (key.isEmpty()) return null
        byName[key]?.let { return it }
        return byName.filterKeys { it.startsWith(key) }.values.distinct().singleOrNull()
            ?.takeIf { key.length >= 2 }
    }

    private fun normalize(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    private fun aliasesFor(name: String): List<String> = when (name) {
        "Psalms" -> listOf("Psalm", "Ps")
        "Song of Solomon" -> listOf("Song of Songs", "Song", "Canticles")
        "Revelation" -> listOf("Revelations")
        "Philemon" -> listOf("Phlm")
        "Judges" -> listOf("Jdg")
        else -> emptyList()
    }
}

/**
 * Turns a passage reference like "Exodus 11:1-12:21", "Jeremiah 36, 45" or "Psalm 119:1-24"
 * into the chapters it touches. Any part of a chapter counts as that chapter.
 */
object PassageParser {
    private val passagePattern = Regex("""^\s*(\d?\s*[A-Za-z][A-Za-z .]*?)\s*(\d[\d\s:,&\-–]*)\s*$""")

    /** Returns the chapters in [passage], or null if it can't be understood. */
    fun chapters(passage: String): List<ChapterRef>? {
        val match = passagePattern.matchEntire(passage) ?: return null
        val book = Bible.findBook(match.groupValues[1]) ?: return null
        val result = linkedSetOf<Int>()
        var previousHadVerse = false

        for (segment in match.groupValues[2].split(',', '&').map { it.trim() }.filter { it.isNotEmpty() }) {
            val (startPart, endPart) = segment.split('-', '–').map { it.trim() }.let { it[0] to it.getOrNull(1) }
            val startChapter = startPart.substringBefore(':').toIntOrNull() ?: return null
            if (':' !in segment && previousHadVerse) {
                // "John 3:16, 18": numbers after a verse reference are more verses.
                continue
            }
            val endChapter = when {
                endPart == null -> startChapter
                ':' in endPart -> endPart.substringBefore(':').toIntOrNull() ?: return null
                ':' in startPart -> startChapter // "3:16-21" is a verse range
                else -> endPart.toIntOrNull() ?: return null
            }
            if (startChapter < 1 || endChapter < startChapter || endChapter > book.chapters) return null
            result += startChapter..endChapter
            previousHadVerse = ':' in segment
        }
        return result.takeIf { it.isNotEmpty() }?.map { ChapterRef(book, it) }
    }
}
