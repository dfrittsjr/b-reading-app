package com.hamferd.breading.data

import android.content.res.AssetManager

/** Loads every `*.txt` plan bundled from data/plans (packaged as the "plans" asset folder). */
class PlanRepository(private val assets: AssetManager) {
    fun loadAll(): List<ReadingPlan> =
        (assets.list(PLANS_DIR) ?: emptyArray())
            .filter { it.endsWith(".txt") }
            .sorted()
            .map { fileName ->
                val text = assets.open("$PLANS_DIR/$fileName").bufferedReader().use { it.readText() }
                PlanParser.parse(fileName.removeSuffix(".txt"), text)
            }
            .filter { it.days.isNotEmpty() }

    private companion object {
        const val PLANS_DIR = "plans"
    }
}
