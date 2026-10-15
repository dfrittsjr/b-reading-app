package com.hamferd.breading.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val selectedPlanId: String?,
    val startDates: Map<String, LocalDate>,
)

/** Persists the selected plan and the start date chosen for each plan. */
class SettingsStore(private val context: Context) {
    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val startDates = prefs.asMap()
            .filterKeys { it.name.startsWith(START_PREFIX) }
            .mapNotNull { (key, value) ->
                val date = (value as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                date?.let { key.name.removePrefix(START_PREFIX) to it }
            }
            .toMap()
        Settings(prefs[SELECTED_PLAN], startDates)
    }

    suspend fun selectPlan(planId: String, defaultStart: LocalDate) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_PLAN] = planId
            val startKey = startKey(planId)
            if (prefs[startKey] == null) prefs[startKey] = defaultStart.toString()
        }
    }

    /** Replaces the selected plan and all start dates (used when importing a backup). */
    suspend fun replaceAll(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs.clear()
            settings.selectedPlanId?.let { prefs[SELECTED_PLAN] = it }
            settings.startDates.forEach { (planId, date) -> prefs[startKey(planId)] = date.toString() }
        }
    }

    suspend fun setStartDate(planId: String, date: LocalDate) {
        context.dataStore.edit { it[startKey(planId)] = date.toString() }
    }

    private companion object {
        const val START_PREFIX = "start_"
        val SELECTED_PLAN = stringPreferencesKey("selected_plan")
        fun startKey(planId: String) = stringPreferencesKey(START_PREFIX + planId)
    }
}
