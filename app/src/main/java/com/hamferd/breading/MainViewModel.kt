package com.hamferd.breading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamferd.breading.data.DayReadings
import com.hamferd.breading.data.PlanDay
import com.hamferd.breading.data.PlanProgress
import com.hamferd.breading.data.PlanRepository
import com.hamferd.breading.data.ProgressStore
import com.hamferd.breading.data.ReadingPlan
import com.hamferd.breading.data.SettingsStore
import com.hamferd.breading.data.dayFor
import com.hamferd.breading.data.readingsOn
import com.hamferd.breading.data.trackedDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface UiState {
    data object Loading : UiState
    data object NoPlans : UiState
    data class Ready(
        val plans: List<ReadingPlan>,
        val plan: ReadingPlan,
        val startDate: LocalDate,
        val today: LocalDate,
        /** The date being viewed. */
        val date: LocalDate,
        val day: PlanDay,
        /** Readings on [date], or null if the plan has no day there. */
        val readings: DayReadings?,
        /** Every plan day counted toward progress. */
        val trackedDays: List<DayReadings>,
        val progress: PlanProgress,
        /** The note for [date]. */
        val note: String,
    ) : UiState {
        val isToday: Boolean get() = date == today
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val progressStore = ProgressStore(app)

    private val plans = flow { emit(PlanRepository(app.assets).loadAll()) }
        .flowOn(Dispatchers.IO)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val today = MutableStateFlow(LocalDate.now())

    /** Days away from today currently being viewed (0 = today). */
    private val dayOffset = MutableStateFlow(0L)

    val state: StateFlow<UiState> = combine(
        plans, settingsStore.settings, progressStore.data, today, dayOffset,
    ) { plans, settings, progressData, today, offset ->
        val plan = plans.firstOrNull { it.id == settings.selectedPlanId } ?: plans.firstOrNull()
            ?: return@combine UiState.NoPlans
        val startDate = settings.startDates[plan.id] ?: today
        val date = today.plusDays(offset)
        val completed = progressData.completed[plan.id].orEmpty()
        val tracked = plan.trackedDays(startDate, today, completed)
        UiState.Ready(
            plans = plans,
            plan = plan,
            startDate = startDate,
            today = today,
            date = date,
            day = plan.dayFor(startDate, date),
            readings = plan.readingsOn(startDate, date, completed),
            trackedDays = tracked,
            progress = PlanProgress.of(tracked, today),
            note = progressData.notes[date].orEmpty(),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    init {
        // Make sure the active plan has a persisted start date so "today" keeps advancing.
        viewModelScope.launch {
            val all = plans.first()
            val settings = settingsStore.settings.first()
            val plan = all.firstOrNull { it.id == settings.selectedPlanId } ?: all.firstOrNull()
            if (plan != null) settingsStore.selectPlan(plan.id, today.value)
        }
    }

    /** Called on resume so the screen rolls over to the new day after midnight. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (now != today.value) {
            today.value = now
            dayOffset.value = 0
        }
    }

    fun selectPlan(planId: String) {
        dayOffset.value = 0
        viewModelScope.launch { settingsStore.selectPlan(planId, today.value) }
    }

    fun setStartDate(date: LocalDate) {
        val ready = state.value as? UiState.Ready ?: return
        dayOffset.value = 0
        viewModelScope.launch { settingsStore.setStartDate(ready.plan.id, date) }
    }

    fun setRead(readingId: String, done: Boolean) {
        val ready = state.value as? UiState.Ready ?: return
        viewModelScope.launch { progressStore.setDone(ready.plan.id, readingId, done) }
    }

    fun saveNote(date: LocalDate, text: String) {
        viewModelScope.launch { progressStore.setNote(date, text) }
    }

    fun previousDay() = dayOffset.update { it - 1 }
    fun nextDay() = dayOffset.update { it + 1 }
    fun goToToday() { dayOffset.value = 0 }
    fun goToDate(date: LocalDate) { dayOffset.value = ChronoUnit.DAYS.between(today.value, date) }
}
