package com.okan.ajanda.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.okan.ajanda.data.prefs.AppSettings
import com.okan.ajanda.ui.calendar.MonthMatrixScreen
import com.okan.ajanda.ui.components.paperBackground
import com.okan.ajanda.ui.edit.TaskEditScreen
import com.okan.ajanda.ui.quickadd.QuickAddRequest
import com.okan.ajanda.ui.quickadd.QuickAddSheet
import com.okan.ajanda.ui.settings.SettingsScreen
import com.okan.ajanda.ui.tasks.TaskListScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

sealed interface LaunchAction {
    data class QuickAdd(val voice: Boolean) : LaunchAction
    data class OpenTask(val id: Long) : LaunchAction
    data object OpenCalendar : LaunchAction
}

object Routes {
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val EDIT = "edit?taskId={taskId}&date={date}"
    fun edit(id: Long) = "edit?taskId=$id"
    fun newFor(date: LocalDate?) = if (date == null) "edit" else "edit?date=${date.toEpochDay()}"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(Routes.TASKS, "Görevler", Icons.Outlined.TaskAlt),
    TopLevel(Routes.CALENDAR, "Ajanda", Icons.Outlined.CalendarMonth),
    TopLevel(Routes.SETTINGS, "Ayarlar", Icons.Outlined.Settings),
)

@Composable
fun AjandaRoot(settings: AppSettings, launchActions: MutableStateFlow<LaunchAction?>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var quickAdd by remember { mutableStateOf<QuickAddRequest?>(null) }
    val action by launchActions.collectAsStateWithLifecycle()

    LaunchedEffect(action) {
        when (val a = action) {
            is LaunchAction.QuickAdd -> quickAdd = QuickAddRequest(LocalDate.now(), startVoice = a.voice)
            is LaunchAction.OpenTask -> if (a.id > 0) nav.navigate(Routes.edit(a.id))
            LaunchAction.OpenCalendar -> nav.navigateTopLevel(Routes.CALENDAR)
            null -> Unit
        }
        launchActions.value = null
    }

    val paperLine = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (topLevels.any { it.route == route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    topLevels.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.navigateTopLevel(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .paperBackground(settings.paperStyle, paperLine)
                .padding(padding)
        ) {
            NavHost(
                navController = nav,
                startDestination = Routes.TASKS,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable(Routes.TASKS) {
                    TaskListScreen(
                        haptics = settings.haptics,
                        onOpenTask = { nav.navigate(Routes.edit(it)) },
                        onAdd = { quickAdd = QuickAddRequest(LocalDate.now()) },
                        onVoice = { quickAdd = QuickAddRequest(LocalDate.now(), startVoice = true) },
                    )
                }
                composable(Routes.CALENDAR) {
                    MonthMatrixScreen(
                        haptics = settings.haptics,
                        onOpenTask = { nav.navigate(Routes.edit(it)) },
                        onCreateForDate = { nav.navigate(Routes.newFor(it)) },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(
                    Routes.EDIT,
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    ),
                    enterTransition = { slideInVertically { it / 6 } + fadeIn() },
                    popExitTransition = { slideOutVertically { it / 6 } + fadeOut() },
                ) {
                    TaskEditScreen(onClose = { nav.popBackStack() })
                }
            }
        }
    }

    quickAdd?.let { req ->
        QuickAddSheet(
            request = req,
            haptics = settings.haptics,
            onDismiss = { quickAdd = null },
            onOpenEditor = { date -> quickAdd = null; nav.navigate(Routes.newFor(date)) },
        )
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
