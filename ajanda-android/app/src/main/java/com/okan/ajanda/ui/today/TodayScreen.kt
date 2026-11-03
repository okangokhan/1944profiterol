package com.okan.ajanda.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.CardDivider
import com.okan.ajanda.ui.components.EmptyLine
import com.okan.ajanda.ui.components.InlineAddField
import com.okan.ajanda.ui.components.ScreenHeader
import com.okan.ajanda.ui.components.SectionHeader
import com.okan.ajanda.ui.components.SoftCard
import com.okan.ajanda.ui.components.TaskLine
import com.okan.ajanda.ui.components.confirmHaptic

@Composable
fun TodayScreen(
    haptics: Boolean,
    onOpenTask: (Long) -> Unit,
    vm: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current

    LaunchedEffect(Unit) {
        vm.deletedEvents.collect { title ->
            val r = snackbar.showSnackbar("“$title” silindi", "Geri al", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp)
        ) {
            item(key = "header") {
                ScreenHeader(
                    overline = DateText.full(s.today).replace(",", ""),
                    title = "Bugün"
                )
            }
            item(key = "add") {
                InlineAddField(text, { text = it }, onSubmit = {
                    if (text.isNotBlank()) { if (haptics) view.confirmHaptic(); vm.add(text); text = "" }
                })
            }
            if (s.overdue.isNotEmpty()) {
                item(key = "h_overdue") { SectionHeader("Geciken", color = MaterialTheme.colorScheme.error) }
                item(key = "c_overdue") { TaskCardList(s.overdue, showDate = true, haptics, vm, onOpenTask) }
            }
            item(key = "h_today") {
                SectionHeader("Bugün", trailing = if (s.todayItems.isEmpty()) null else "${s.todayDone}/${s.todayItems.size}")
            }
            item(key = "c_today") {
                if (s.todayItems.isEmpty() && !s.loading) {
                    SoftCard { EmptyLine(if (s.upcomingWeek > 0) "Bugün boş · bu hafta ${s.upcomingWeek} görev daha var" else "Bugün için görev yok") }
                } else TaskCardList(s.todayItems, showDate = false, haptics, vm, onOpenTask)
            }
            if (s.undated.isNotEmpty()) {
                item(key = "h_undated") { SectionHeader("Notlar · Tarihsiz") }
                item(key = "c_undated") { TaskCardList(s.undated, showDate = false, haptics, vm, onOpenTask) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).statusBarsPadding())
    }
}

@Composable
private fun TaskCardList(
    items: List<TaskWithDetails>,
    showDate: Boolean,
    haptics: Boolean,
    vm: TodayViewModel,
    onOpenTask: (Long) -> Unit,
) {
    SoftCard(Modifier.animateContentSize()) {
        items.forEachIndexed { i, d ->
            androidx.compose.runtime.key(d.task.id) {
                if (i > 0) CardDivider()
                TaskLine(
                    details = d,
                    onToggle = { vm.toggle(d.task.id) },
                    onClick = { onOpenTask(d.task.id) },
                    haptics = haptics,
                    showDate = showDate,
                    onDelete = { vm.delete(d.task.id) },
                )
            }
        }
    }
}
