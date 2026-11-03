package com.okan.ajanda.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.repository.TaskRepository
import com.okan.ajanda.domain.ParsedCommand
import kotlinx.coroutines.launch
import java.time.LocalDate

class QuickAddViewModel(private val repo: TaskRepository) : ViewModel() {
    fun add(cmd: ParsedCommand, fallbackDate: LocalDate?) = viewModelScope.launch {
        repo.addParsed(cmd, fallbackDate)
    }
}
