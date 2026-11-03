package com.okan.ajanda.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.okan.ajanda.AjandaApp
import com.okan.ajanda.ui.calendar.CalendarViewModel
import com.okan.ajanda.ui.edit.TaskEditViewModel
import com.okan.ajanda.ui.quickadd.QuickAddViewModel
import com.okan.ajanda.ui.settings.SettingsViewModel
import com.okan.ajanda.ui.today.TodayViewModel
import com.okan.ajanda.ui.week.WeekViewModel

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { TodayViewModel(app().container.taskRepository) }
        initializer { WeekViewModel(app().container.taskRepository, app().container.settingsRepository) }
        initializer { CalendarViewModel(app().container.taskRepository, app().container.settingsRepository) }
        initializer { TaskEditViewModel(app().container.taskRepository) }
        initializer { QuickAddViewModel(app().container.taskRepository) }
        initializer { SettingsViewModel(app().container.settingsRepository, app().container.taskRepository) }
    }
}

private fun CreationExtras.app(): AjandaApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AjandaApp
