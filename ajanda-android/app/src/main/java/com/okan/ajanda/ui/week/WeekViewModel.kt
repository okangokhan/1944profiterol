package com.okan.ajanda.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.local.TaskOccurrence
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.data.prefs.SettingsRepository
import com.okan.ajanda.data.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekUiState(
    val start: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val days: List<Pair<LocalDate, List<TaskOccurrence>>> = emptyList(),
    val deadlines: List<TaskWithDetails> = emptyList(),
    val isCurrent: Boolean = true,
) {
    val end: LocalDate get() = start.plusDays(6)
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(private val repo: TaskRepository, settings: SettingsRepository) : ViewModel() {
    private val offset = MutableStateFlow(0L)

    val state: StateFlow<WeekUiState> =
        combine(offset, settings.settings.map { it.weekStartsMonday }.distinctUntilChanged()) { o, monday -> o to monday }
            .flatMapLatest { (o, monday) ->
                val today = LocalDate.now()
                val first = if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
                val start = today.with(TemporalAdjusters.previousOrSame(first)).plusWeeks(o)
                val end = start.plusDays(6)
                combine(repo.observeRange(start, end), repo.observeOverdue(today)) { occ, overdue ->
                    val grouped = occ.groupBy { it.date }
                    val inWeek = occ.filter { !it.isVirtual && !it.task.isCompleted }.map { it.details }
                    val deadlines = ((if (o == 0L) overdue else emptyList()) + inWeek)
                        .distinctBy { it.task.id }
                        .filter { it.task.dueDate != null && (o != 0L || it.task.dueDate!! >= today || !it.task.isCompleted) }
                        .sortedWith(compareBy({ it.task.dueDate }, { it.task.dueTime ?: java.time.LocalTime.MAX }))
                    WeekUiState(
                        start = start, today = today,
                        days = (0L..6L).map { start.plusDays(it) }.map { it to grouped[it].orEmpty() },
                        deadlines = deadlines,
                        isCurrent = o == 0L,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    fun next() { offset.value += 1 }
    fun previous() { offset.value -= 1 }
    fun current() { offset.value = 0 }

    fun toggle(occ: TaskOccurrence) = viewModelScope.launch { if (!occ.isVirtual) repo.toggleComplete(occ.task.id) }
    fun toggle(id: Long) = viewModelScope.launch { repo.toggleComplete(id) }
}
