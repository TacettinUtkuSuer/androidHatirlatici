package com.utkusuer.hatirlatici.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    /** Gün içi varsayılan hatırlatma saatleri (dakika). Varsayılan: 10:00, 14:00, 19:00. */
    val defaultReminderMinutes: List<Int> = listOf(10 * 60, 14 * 60, 19 * 60),
    val persistentNotificationsEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    /** Ana ekranda/widget'ta "yaklaşan" sayılacak gün sayısı. */
    val upcomingWindowDays: Int = 14,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            defaultReminderMinutes = p[KEY_TIMES]?.let(::parseMinutes) ?: AppSettings().defaultReminderMinutes,
            persistentNotificationsEnabled = p[KEY_PERSISTENT] ?: true,
            remindersEnabled = p[KEY_REMINDERS] ?: true,
            upcomingWindowDays = p[KEY_UPCOMING] ?: 14,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setDefaultReminderMinutes(minutes: List<Int>) = context.dataStore.edit {
        it[KEY_TIMES] = minutes.filter { m -> m in 0 until 24 * 60 }.distinct().sorted().joinToString(",")
    }

    suspend fun setPersistentEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_PERSISTENT] = enabled }
    suspend fun setRemindersEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_REMINDERS] = enabled }
    suspend fun setUpcomingWindowDays(days: Int) = context.dataStore.edit { it[KEY_UPCOMING] = days.coerceIn(1, 60) }

    private fun parseMinutes(raw: String): List<Int> =
        raw.split(',').mapNotNull { it.trim().toIntOrNull() }

    private companion object {
        val KEY_TIMES = stringPreferencesKey("default_reminder_minutes")
        val KEY_PERSISTENT = booleanPreferencesKey("persistent_enabled")
        val KEY_REMINDERS = booleanPreferencesKey("reminders_enabled")
        val KEY_UPCOMING = intPreferencesKey("upcoming_window_days")
    }
}
