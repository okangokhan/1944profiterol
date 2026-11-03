package com.okan.ajanda.ui.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.TagDot
import com.okan.ajanda.ui.components.TaskRow
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(
    haptics: Boolean,
    onOpenTask: (Long) -> Unit,
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    vm: TaskListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val today = LocalDate.now()

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is TaskListEvent.Deleted -> {
                    val r = snackbar.showSnackbar("“${e.title}” silindi", "Geri al", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.undoDelete()
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Text("Görevler", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${DateText.full(today)} · ${state.todayCount} bugün, ${state.openCount} açık",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = onVoice,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) { Icon(Icons.Filled.Mic, "Sesli görev") }
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Filled.Add, "Görev ekle") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TaskFilter.entries) { f ->
                        FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                    }
                }
            }
            if (state.tags.isNotEmpty()) {
                item(key = "tags") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.tags, key = { it.id }) { tag ->
                            FilterChip(
                                selected = state.tagFilter == tag.id,
                                onClick = { vm.setTagFilter(tag.id) },
                                label = { Text(tag.name) },
                                leadingIcon = { TagDot(tag.color) }
                            )
                        }
                    }
                }
            }
            if (!state.loading && state.sections.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
            state.sections.forEach { section ->
                item(key = "h_${section.key}") {
                    Text(
                        section.title.uppercase(com.okan.ajanda.domain.TR),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (section.key == "overdue") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp).animateItem()
                    )
                }
                items(section.items, key = { "t_${it.task.id}" }) { d ->
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) { vm.delete(d.task.id); true } else false
                    })
                    SwipeToDismissBox(
                        state = dismiss,
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier.animateItem(),
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) { Icon(Icons.Outlined.Delete, "Sil", tint = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    ) {
                        TaskRow(
                            details = d,
                            onToggle = { vm.toggle(d.task.id) },
                            onClick = { onOpenTask(d.task.id) },
                            haptics = haptics,
                            today = today,
                            modifier = Modifier.animateContentSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.TaskAlt, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text("Liste tertemiz", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Yeni görev için + düğmesine dokunun ya da\n“yarın saat 10 sunum #iş acil” gibi sesli komut verin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
