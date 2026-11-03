package com.okan.ajanda.ui.calendar

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.local.TaskOccurrence
import com.okan.ajanda.data.prefs.SettingsRepository
import com.okan.ajanda.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val mondayFirst: Boolean = false,
    val days: List<LocalDate> = MonthMatrix.days(YearMonth.now(), false),
    val byDate: Map<LocalDate, List<TaskOccurrence>> = emptyMap(),
) {
    val monthTotal: Int get() = byDate.filterKeys { YearMonth.from(it) == month }.values.sumOf { it.size }
    val monthDone: Int get() = byDate.filterKeys { YearMonth.from(it) == month }.values.sumOf { l -> l.count { it.task.isCompleted && !it.isVirtual } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repo: TaskRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    val selectedDay = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<CalendarUiState> =
        combine(month, settings.settings.map { it.weekStartsMonday }.distinctUntilChanged()) { m, monday -> m to monday }
            .flatMapLatest { (m, monday) ->
                val days = MonthMatrix.days(m, monday)
                repo.observeRange(days.first(), days.last()).map { occ ->
                    CalendarUiState(m, monday, days, occ.groupBy { it.date })
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun next() { month.value = month.value.plusMonths(1) }
    fun previous() { month.value = month.value.minusMonths(1) }
    fun goToday() { month.value = YearMonth.now() }
    fun select(day: LocalDate?) { selectedDay.value = day }

    fun toggle(occ: TaskOccurrence) = viewModelScope.launch {
        if (!occ.isVirtual) repo.toggleComplete(occ.task.id)
    }

    fun quickAdd(date: LocalDate, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) repo.quickAdd(title, date)
    }

    fun export(context: Context, format: MonthExporter.Format, onReady: (Uri, String) -> Unit) = viewModelScope.launch {
        val s = state.value
        val uri = withContext(Dispatchers.Default) { MonthExporter.export(context, s, format) }
        onReady(uri, format.mime)
    }
}
