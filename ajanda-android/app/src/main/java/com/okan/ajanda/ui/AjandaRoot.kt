package com.okan.ajanda.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.okan.ajanda.data.prefs.AppSettings
import com.okan.ajanda.ui.calendar.MonthMatrixScreen
import com.okan.ajanda.ui.components.FloatingNavBar
import com.okan.ajanda.ui.components.NavItem
import com.okan.ajanda.ui.components.paperBackground
import com.okan.ajanda.ui.edit.EditRequest
import com.okan.ajanda.ui.edit.TaskEditSheet
import com.okan.ajanda.ui.quickadd.QuickAddRequest
import com.okan.ajanda.ui.quickadd.QuickAddSheet
import com.okan.ajanda.ui.settings.SettingsScreen
import com.okan.ajanda.ui.today.TodayScreen
import com.okan.ajanda.ui.week.WeekScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

sealed interface LaunchAction {
    data class QuickAdd(val voice: Boolean) : LaunchAction
    data class OpenTask(val id: Long) : LaunchAction
    data object OpenCalendar : LaunchAction
    data object OpenWeek : LaunchAction
}

object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val MONTH = "month"
    const val SETTINGS = "settings"
}

private val navItems = listOf(
    NavItem(Routes.TODAY, "Bugün"),
    NavItem(Routes.WEEK, "Hafta"),
    NavItem(Routes.MONTH, "Ay"),
    NavItem(Routes.SETTINGS, null, Icons.Outlined.Tune),
)

@Composable
fun AjandaRoot(settings: AppSettings, launchActions: MutableStateFlow<LaunchAction?>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var quickAdd by remember { mutableStateOf<QuickAddRequest?>(null) }
    var edit by remember { mutableStateOf<EditRequest?>(null) }
    val action by launchActions.collectAsStateWithLifecycle()

    LaunchedEffect(action) {
        when (val a = action) {
            is LaunchAction.QuickAdd -> quickAdd = QuickAddRequest(LocalDate.now(), startVoice = a.voice)
            is LaunchAction.OpenTask -> if (a.id > 0) edit = EditRequest(a.id)
            LaunchAction.OpenCalendar -> nav.navigateTopLevel(Routes.MONTH)
            LaunchAction.OpenWeek -> nav.navigateTopLevel(Routes.WEEK)
            null -> Unit
        }
        launchActions.value = null
    }

    val openTask: (Long) -> Unit = { edit = EditRequest(it) }
    val newFor: (LocalDate?) -> Unit = { edit = EditRequest(0, it) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .paperBackground(settings.paperStyle, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.TODAY) { TodayScreen(haptics = settings.haptics, onOpenTask = openTask) }
            composable(Routes.WEEK) { WeekScreen(haptics = settings.haptics, onOpenTask = openTask, onAddForDate = newFor) }
            composable(Routes.MONTH) {
                MonthMatrixScreen(haptics = settings.haptics, onOpenTask = openTask, onCreateForDate = newFor)
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
        FloatingNavBar(
            items = navItems,
            selected = route,
            onSelect = { nav.navigateTopLevel(it) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)
        )
    }

    quickAdd?.let { req ->
        QuickAddSheet(
            request = req,
            haptics = settings.haptics,
            onDismiss = { quickAdd = null },
            onOpenEditor = { date -> quickAdd = null; newFor(date) },
        )
    }
    edit?.let { req -> TaskEditSheet(request = req, onClose = { edit = null }) }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
