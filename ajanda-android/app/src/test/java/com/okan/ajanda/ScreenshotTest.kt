package com.okan.ajanda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.TaskEntity
import com.okan.ajanda.data.prefs.ThemeMode
import com.okan.ajanda.domain.RecurrenceCalculator
import com.okan.ajanda.ui.calendar.MonthMatrixScreen
import com.okan.ajanda.ui.theme.AjandaTheme
import com.okan.ajanda.ui.today.TodayScreen
import com.okan.ajanda.ui.week.WeekScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Ekranları örnek verilerle çizip PNG olarak kaydeder (build/shots/). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun seed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AjandaApp>()
        val repo = app.container.taskRepository
        if (repo.observeTasks().first().isNotEmpty()) return@runBlocking
        val tags = repo.observeTags().first().associateBy { it.name }
        val today = LocalDate.now()
        fun id(n: String) = listOfNotNull(tags[n]?.id)
        val old = Instant.now().minusSeconds(86400L * 6)
        repo.saveTask(TaskEntity(title = "Sınavlar var", dueDate = today.minusDays(3), createdAt = old), emptyList(), id("Okul"))
        repo.saveTask(TaskEntity(title = "5. Sınıflar için örnek sınav hazırla", dueDate = today.minusDays(3), createdAt = old), emptyList(), id("Diğer"))
        repo.saveTask(TaskEntity(title = "LGS koçluk görüşmeleri", dueDate = today, recurrence = Recurrence.WEEKLY,
            recurrenceDays = RecurrenceCalculator.bit(today.dayOfWeek), recurrenceEndDate = today.plusMonths(8)), emptyList(), id("LGS Koçluk"))
        repo.saveTask(TaskEntity(title = "Tez bölüm 3 taslağı", dueDate = today.plusDays(2), dueTime = LocalTime.of(17, 0),
            priority = Priority.HIGH, createdAt = old), emptyList(), id("Doktora"))
        repo.saveTask(TaskEntity(title = "Dükkan siparişleri", dueDate = today.plusDays(4), createdAt = Instant.now().minusSeconds(86400)), emptyList(), id("1944"))
        repo.saveTask(TaskEntity(title = "Annemi ara", dueDate = today, dueTime = LocalTime.of(20, 0), reminderMinutesBefore = 0), emptyList(), id("Kişisel & Aile"))
        repo.saveTask(TaskEntity(title = "Makale okuma listesi", dueDate = today.plusDays(9), createdAt = old), emptyList(), id("Doktora"))
        repo.saveTask(TaskEntity(title = "Kitap önerileri", notes = "Sapiens, Kürk Mantolu Madonna"), emptyList(), emptyList())
        val done = repo.saveTask(TaskEntity(title = "Veli toplantısı notları", dueDate = today), emptyList(), id("Okul"))
        repo.toggleComplete(done)
    }

    private fun shot(name: String, dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            AjandaTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.mainClock.advanceTimeBy(2000)
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun today() = shot("1_bugun") { TodayScreen(haptics = false, onOpenTask = {}) }
    @Test fun week() = shot("2_hafta") { WeekScreen(haptics = false, onOpenTask = {}, onAddForDate = {}) }
    @Test fun month() = shot("3_ay") { MonthMatrixScreen(haptics = false, onOpenTask = {}, onCreateForDate = {}) }
    @Test fun todayDark() = shot("4_bugun_koyu", dark = true) { TodayScreen(haptics = false, onOpenTask = {}) }
}
