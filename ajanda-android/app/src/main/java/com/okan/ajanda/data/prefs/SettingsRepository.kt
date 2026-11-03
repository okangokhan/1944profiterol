package com.okan.ajanda.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Aydınlık"), DARK("Karanlık") }
enum class PaperStyle(val label: String) { PLAIN("Düz"), LINED("Çizgili"), GRID("Kareli"), DOTTED("Noktalı") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val paperStyle: PaperStyle = PaperStyle.DOTTED,
    val dynamicColor: Boolean = false,
    val weekStartsMonday: Boolean = false,
    val haptics: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val paper = stringPreferencesKey("paper")
        val dynamic = booleanPreferencesKey("dynamic")
        val monday = booleanPreferencesKey("monday")
        val haptics = booleanPreferencesKey("haptics")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            paperStyle = p[Keys.paper]?.let { runCatching { PaperStyle.valueOf(it) }.getOrNull() } ?: PaperStyle.DOTTED,
            dynamicColor = p[Keys.dynamic] ?: false,
            weekStartsMonday = p[Keys.monday] ?: false,
            haptics = p[Keys.haptics] ?: true,
        )
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setTheme(mode: ThemeMode) = set(Keys.theme, mode.name)
    suspend fun setPaper(style: PaperStyle) = set(Keys.paper, style.name)
    suspend fun setDynamicColor(enabled: Boolean) = set(Keys.dynamic, enabled)
    suspend fun setWeekStartsMonday(enabled: Boolean) = set(Keys.monday, enabled)
    suspend fun setHaptics(enabled: Boolean) = set(Keys.haptics, enabled)
}
