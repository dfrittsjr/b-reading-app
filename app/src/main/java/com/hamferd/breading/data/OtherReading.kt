package com.hamferd.breading.data

import java.time.LocalDate

/** Something read outside the plan, e.g. a sermon text at church. */
data class OtherReading(val id: String, val date: LocalDate, val passage: String, val source: String) {
    fun encode(): String = listOf(id, date.toString(), passage, source).joinToString(SEPARATOR) { it.clean() }

    companion object {
        private const val SEPARATOR = "\t"
        private fun String.clean() = replace(Regex("""[\t\r\n]+"""), " ").trim()

        fun decode(value: String): OtherReading? {
            val parts = value.split(SEPARATOR)
            if (parts.size < 3) return null
            val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
            return OtherReading(parts[0], date, parts[2], parts.getOrElse(3) { "" })
        }
    }
}
