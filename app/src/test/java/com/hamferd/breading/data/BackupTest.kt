package com.hamferd.breading.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val oct14 = LocalDate.of(2026, 10, 14)

    private val snapshot = Snapshot(
        Settings("mcheyne", mapOf("mcheyne" to oct13)),
        ProgressData(
            completed = mapOf("mcheyne" to listOf(Completion("2026-286/0", oct13), Completion("2026-286/1", null))),
            notes = mapOf(oct13 to "Line one\nline \"two\""),
            otherReadings = listOf(OtherReading("1-a", oct14, "Romans 8", "Church")),
        ),
    )

    @Test
    fun roundTripsThroughJson() {
        val text = Backup.encode(Backup.of(snapshot, "2026-10-14T21:00:00Z"))
        val restored = Backup.decode(text).toSnapshot()
        assertEquals(snapshot, restored)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"hello": 1}""") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) {
            Backup.decode("""{"format": "b-reading-app backup", "version": 99, "exportedAt": "x"}""")
        }
    }

    @Test
    fun mergeKeepsEverythingOnThePhone() {
        // A fresh install: default start date of today, one reading checked, one note written.
        val phone = Snapshot(
            Settings("mcheyne", mapOf("mcheyne" to oct14)),
            ProgressData(
                completed = mapOf("mcheyne" to listOf(Completion("2026-287/0", oct14), Completion("2026-286/1", oct14))),
                notes = mapOf(oct13 to "", oct14 to "Phone note"),
                otherReadings = listOf(OtherReading("2-b", oct14, "John 1", "")),
            ),
        )
        val merged = merge(phone, snapshot)

        assertEquals(mapOf("mcheyne" to oct13), merged.settings.startDates) // backup's start date wins
        assertEquals(
            setOf(Completion("2026-286/0", oct13), Completion("2026-286/1", oct14), Completion("2026-287/0", oct14)),
            merged.progress.completed.getValue("mcheyne").toSet(),
        )
        assertEquals(mapOf(oct13 to "Line one\nline \"two\"", oct14 to "Phone note"), merged.progress.notes)
        assertEquals(setOf("1-a", "2-b"), merged.progress.otherReadings.map { it.id }.toSet())
    }
}
