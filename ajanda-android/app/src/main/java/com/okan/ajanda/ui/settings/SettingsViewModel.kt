package com.okan.ajanda.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.local.TagEntity
import com.okan.ajanda.data.prefs.AppSettings
import com.okan.ajanda.data.prefs.PaperStyle
import com.okan.ajanda.data.prefs.SettingsRepository
import com.okan.ajanda.data.prefs.ThemeMode
import com.okan.ajanda.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val taskRepo: TaskRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val tags: StateFlow<List<TagEntity>> =
        taskRepo.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTheme(m: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(m) }
    fun setPaper(p: PaperStyle) = viewModelScope.launch { settingsRepo.setPaper(p) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(v) }
    fun setMonday(v: Boolean) = viewModelScope.launch { settingsRepo.setWeekStartsMonday(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsRepo.setHaptics(v) }
    fun createTag(name: String, color: Int) = viewModelScope.launch { taskRepo.createTag(name, color) }
    fun deleteTag(id: Long) = viewModelScope.launch { taskRepo.deleteTag(id) }
}
