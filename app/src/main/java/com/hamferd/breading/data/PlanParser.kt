package com.hamferd.breading.data

/**
 * Parses the plain-text plan format (see data/plans/README.md):
 *
 *   title: My Plan
 *   description: Optional one-line description
 *   schedule: yearly          (optional; day 1 = January 1 instead of a chosen start date)
 *   labels: Family; Secret    (optional; names for each passage slot)
 *   # comment
 *   Genesis 1-2; Matthew 1
 *   Genesis 3-4; Matthew 2
 *
 * Every other non-blank line is one day; passages within a day are separated by ';'.
 */
object PlanParser {
    private val metadataKeys = setOf("title", "description", "schedule", "labels")

    fun parse(id: String, text: String): ReadingPlan {
        val metadata = mutableMapOf<String, String>()
        val days = mutableListOf<List<String>>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val key = line.substringBefore(':', "").trim().lowercase()
            if (key in metadataKeys) {
                metadata[key] = line.substringAfter(':').trim()
                continue
            }

            val passages = line.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            if (passages.isNotEmpty()) days += passages
        }

        return ReadingPlan(
            id = id,
            title = metadata["title"]?.takeIf { it.isNotEmpty() } ?: id,
            description = metadata["description"]?.takeIf { it.isNotEmpty() },
            days = days,
            schedule = when (metadata["schedule"]?.lowercase()) {
                "yearly" -> Schedule.YEARLY
                else -> Schedule.FROM_START_DATE
            },
            labels = metadata["labels"]?.split(';')?.map { it.trim() }.orEmpty(),
        )
    }
}
