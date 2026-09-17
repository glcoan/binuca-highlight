package com.binuca.highlight.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.binuca.highlight.capture.CaptureQuality
import com.binuca.highlight.capture.ClipDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val remoteDuration: ClipDuration = ClipDuration.TWENTY_SECONDS,
    val quality: CaptureQuality = CaptureQuality.FULL_HD,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
)

private val Context.binucaSettings by preferencesDataStore("binuca_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.binucaSettings.data.map { values ->
        AppSettings(
            remoteDuration = runCatching {
                ClipDuration.fromSeconds(values[REMOTE_DURATION] ?: 20)
            }.getOrDefault(ClipDuration.TWENTY_SECONDS),
            quality = runCatching {
                CaptureQuality.valueOf(values[QUALITY] ?: CaptureQuality.FULL_HD.name)
            }.getOrDefault(CaptureQuality.FULL_HD),
            soundEnabled = values[SOUND] ?: true,
            vibrationEnabled = values[VIBRATION] ?: true,
            voiceEnabled = values[VOICE] ?: true,
        )
    }

    suspend fun setRemoteDuration(value: ClipDuration) = context.binucaSettings.edit {
        it[REMOTE_DURATION] = value.seconds
    }

    suspend fun setQuality(value: CaptureQuality) = context.binucaSettings.edit {
        it[QUALITY] = value.name
    }

    suspend fun setSound(enabled: Boolean) = context.binucaSettings.edit { it[SOUND] = enabled }
    suspend fun setVibration(enabled: Boolean) = context.binucaSettings.edit { it[VIBRATION] = enabled }
    suspend fun setVoice(enabled: Boolean) = context.binucaSettings.edit { it[VOICE] = enabled }

    private companion object {
        val REMOTE_DURATION = intPreferencesKey("remote_duration")
        val QUALITY = stringPreferencesKey("quality")
        val SOUND = booleanPreferencesKey("sound")
        val VIBRATION = booleanPreferencesKey("vibration")
        val VOICE = booleanPreferencesKey("voice")
    }
}
