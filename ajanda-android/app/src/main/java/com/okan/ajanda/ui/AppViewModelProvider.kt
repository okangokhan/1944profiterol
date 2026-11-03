package com.okan.ajanda.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.okan.ajanda.AjandaApp
import com.okan.ajanda.ui.calendar.CalendarViewModel
import com.okan.ajanda.ui.edit.TaskEditViewModel
import com.okan.ajanda.ui.quickadd.QuickAddViewModel
import com.okan.ajanda.ui.settings.SettingsViewModel
import com.okan.ajanda.ui.tasks.TaskListViewModel

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { TaskListViewModel(app().container.taskRepository) }
        initializer { CalendarViewModel(app().container.taskRepository, app().container.settingsRepository) }
        initializer { TaskEditViewModel(createSavedStateHandle(), app().container.taskRepository) }
        initializer { QuickAddViewModel(app().container.taskRepository) }
        initializer { SettingsViewModel(app().container.settingsRepository, app().container.taskRepository) }
    }
}

private fun CreationExtras.app(): AjandaApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AjandaApp
