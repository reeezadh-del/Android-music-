package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.EqualizerPreset
import com.example.data.model.RepeatModeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ava_playback_prefs")

data class StoredPlaybackState(
    val lastTrackId: String? = null,
    val lastPositionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.ALL,
    val equalizerEnabled: Boolean = true,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.ROAD_TRIP,
    val customBandLevelsMb: List<Int> = EqualizerPreset.ROAD_TRIP.bandOffsetsMb,
    val preampDb: Float = 0f,
    val bassBoostPercent: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val skipSilenceEnabled: Boolean = false
)

class PlaybackPreferences(private val context: Context) {

    private object Keys {
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val CUSTOM_BAND_LEVELS = stringPreferencesKey("custom_band_levels_mb")
        val PREAMP_DB = floatPreferencesKey("preamp_db")
        val BASS_BOOST_PERCENT = intPreferencesKey("bass_boost_percent")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
    }

    val stateFlow: Flow<StoredPlaybackState> = context.dataStore.data.map { prefs ->
        val repeatStr = prefs[Keys.REPEAT_MODE] ?: RepeatModeOption.ALL.name
        val presetStr = prefs[Keys.EQUALIZER_PRESET] ?: EqualizerPreset.ROAD_TRIP.name
        val preset = runCatching { EqualizerPreset.valueOf(presetStr) }
            .getOrDefault(EqualizerPreset.ROAD_TRIP)
        val parsedBands = prefs[Keys.CUSTOM_BAND_LEVELS]
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 }
            ?: preset.bandOffsetsMb

        StoredPlaybackState(
            lastTrackId = prefs[Keys.LAST_TRACK_ID],
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = runCatching { RepeatModeOption.valueOf(repeatStr) }
                .getOrDefault(RepeatModeOption.ALL),
            equalizerEnabled = prefs[Keys.EQUALIZER_ENABLED] ?: true,
            equalizerPreset = preset,
            customBandLevelsMb = parsedBands,
            preampDb = prefs[Keys.PREAMP_DB] ?: 0f,
            bassBoostPercent = prefs[Keys.BASS_BOOST_PERCENT] ?: 0,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            skipSilenceEnabled = prefs[Keys.SKIP_SILENCE] ?: false
        )
    }

    suspend fun getSnapshot(): StoredPlaybackState = stateFlow.first()

    suspend fun saveLastTrack(trackId: String, positionMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_TRACK_ID] = trackId
            prefs[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun setRepeatMode(mode: RepeatModeOption) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REPEAT_MODE] = mode.name
        }
    }

    suspend fun setEqualizerState(
        enabled: Boolean,
        preset: EqualizerPreset,
        bandLevelsMb: List<Int>,
        preampDb: Float,
        bassBoostPercent: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EQUALIZER_ENABLED] = enabled
            prefs[Keys.EQUALIZER_PRESET] = preset.name
            prefs[Keys.CUSTOM_BAND_LEVELS] = bandLevelsMb.joinToString(",")
            prefs[Keys.PREAMP_DB] = preampDb
            prefs[Keys.BASS_BOOST_PERCENT] = bassBoostPercent
        }
    }

    suspend fun setMedia3PlaybackSettings(speed: Float, skipSilence: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_SPEED] = speed
            prefs[Keys.SKIP_SILENCE] = skipSilence
        }
    }
}
