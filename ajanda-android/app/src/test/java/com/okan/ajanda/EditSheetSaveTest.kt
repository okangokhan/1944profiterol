package com.okan.ajanda

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.okan.ajanda.data.prefs.ThemeMode
import com.okan.ajanda.domain.VoiceCommandParser
import com.okan.ajanda.ui.edit.EditRequest
import com.okan.ajanda.ui.edit.TaskEditSheet
import com.okan.ajanda.ui.theme.AjandaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.time.LocalDate

/** "Detaylı görev ekle" sayfasında Kaydet davranışı. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xxhdpi")
class EditSheetSaveTest {
    @get:Rule val compose = createComposeRule()
    private var open by mutableStateOf(true)

    private fun show(req: EditRequest) {
        compose.setContent {
            AjandaTheme(ThemeMode.LIGHT, false) { if (open) TaskEditSheet(req) { open = false } }
        }
        compose.waitForIdle()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Kaydet").assertExists() }.isSuccess }
    }

    private fun titlesEventually(expected: String): List<String> {
        val app = ApplicationProvider.getApplicationContext<AjandaApp>()
        var titles = emptyList<String>()
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            titles = runBlocking { app.container.taskRepository.observeTasks().first().map { it.task.title } }
            if (expected in titles) return titles
            Thread.sleep(100)
        }
        return titles
    }

    @Test fun newTaskSavesAndClosesImmediately() {
        show(EditRequest(0, LocalDate.now().plusDays(2)))
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Detaylı test görevi")
        compose.onNodeWithText("Kaydet").performClick()
        compose.waitUntil(2_000) { !open }
        val titles = titlesEventually("Detaylı test görevi")
        assertTrue("kaydedilmedi: $titles", "Detaylı test görevi" in titles)
    }

    @Test fun blankTitleShowsWarningAndStaysOpen() {
        show(EditRequest(0, LocalDate.now()))
        compose.onNodeWithText("Kaydet").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Kaydetmek için bir başlık yaz").assertExists()
        assertTrue(open)
    }

    @Test fun quickAddTextCarriesIntoEditor() {
        show(EditRequest(0, LocalDate.now(), VoiceCommandParser.parse("yarın saat 15 tez toplantısı #Doktora")))
        compose.onNodeWithText("Tez toplantısı").assertExists()
        compose.onNodeWithText("15:00").assertExists()
        compose.onNodeWithText("Kaydet").performClick()
        compose.waitUntil(2_000) { !open }
    }
}
