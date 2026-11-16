package com.example.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.musicDataStore: DataStore<Preferences> by preferencesDataStore(name = "ava_simple_music_prefs")

data class StoredMusicPrefs(
    val favoriteIds: Set<String> = emptySet(),
    val importedUris: Set<String> = emptySet(),
    val favoriteOnlineTracks: List<MusicTrack> = emptyList(),
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val lastTrackId: String? = null,
    val isGuestMode: Boolean = false
)

class MusicPreferences(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
        val IMPORTED_URIS = stringSetPreferencesKey("imported_uris")
        val FAVORITE_ONLINE_TRACKS = stringPreferencesKey("favorite_online_tracks")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val IS_GUEST_MODE = booleanPreferencesKey("is_guest_mode")
    }

    val prefsFlow: Flow<StoredMusicPrefs> = context.musicDataStore.data.map { prefs ->
        val repeatStr = prefs[Keys.REPEAT_MODE] ?: RepeatModeOption.OFF.name
        val onlineJson = prefs[Keys.FAVORITE_ONLINE_TRACKS]
        val onlineTracks = if (!onlineJson.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<MusicTrack>>(onlineJson) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        StoredMusicPrefs(
            favoriteIds = prefs[Keys.FAVORITE_IDS] ?: emptySet(),
            importedUris = prefs[Keys.IMPORTED_URIS] ?: emptySet(),
            favoriteOnlineTracks = onlineTracks,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = runCatching { RepeatModeOption.valueOf(repeatStr) }
                .getOrDefault(RepeatModeOption.OFF),
            lastTrackId = prefs[Keys.LAST_TRACK_ID],
            isGuestMode = prefs[Keys.IS_GUEST_MODE] ?: false
        )
    }

    suspend fun toggleFavorite(trackId: String, trackSnapshot: MusicTrack? = null): Boolean {
        var added = false
        context.musicDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS]?.toMutableSet() ?: mutableSetOf()
            val currentOnline = prefs[Keys.FAVORITE_ONLINE_TRACKS]
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString<List<MusicTrack>>(it) }.getOrDefault(emptyList()) }
                ?.toMutableList()
                ?: mutableListOf()

            if (current.contains(trackId)) {
                current.remove(trackId)
                currentOnline.removeAll { it.id == trackId }
                added = false
            } else {
                current.add(trackId)
                if (trackSnapshot != null && trackSnapshot.isOnline) {
                    currentOnline.removeAll { it.id == trackId }
                    currentOnline.add(0, trackSnapshot.copy(isFavorite = true))
                }
                added = true
            }
            prefs[Keys.FAVORITE_IDS] = current
            prefs[Keys.FAVORITE_ONLINE_TRACKS] = json.encodeToString(currentOnline)
        }
        return added
    }

    suspend fun addImportedUris(uris: List<String>) {
        if (uris.isEmpty()) return
        context.musicDataStore.edit { prefs ->
            val current = prefs[Keys.IMPORTED_URIS]?.toMutableSet() ?: mutableSetOf()
            current.addAll(uris)
            prefs[Keys.IMPORTED_URIS] = current
        }
    }

    suspend fun removeImportedUri(uri: String) {
        context.musicDataStore.edit { prefs ->
            val current = prefs[Keys.IMPORTED_URIS]?.toMutableSet() ?: mutableSetOf()
            current.remove(uri)
            prefs[Keys.IMPORTED_URIS] = current
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.musicDataStore.edit { prefs ->
            prefs[Keys.SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun setRepeatMode(mode: RepeatModeOption) {
        context.musicDataStore.edit { prefs ->
            prefs[Keys.REPEAT_MODE] = mode.name
        }
    }

    suspend fun saveLastTrackId(trackId: String) {
        context.musicDataStore.edit { prefs ->
            prefs[Keys.LAST_TRACK_ID] = trackId
        }
    }

    suspend fun restoreFromDriveSnapshot(
        favoriteIds: Set<String>,
        favoriteOnlineTracks: List<MusicTrack>,
        shuffleEnabled: Boolean,
        repeatMode: RepeatModeOption,
        lastTrackId: String?
    ) {
        context.musicDataStore.edit { prefs ->
            prefs[Keys.FAVORITE_IDS] = favoriteIds
            prefs[Keys.FAVORITE_ONLINE_TRACKS] = json.encodeToString(favoriteOnlineTracks)
            prefs[Keys.SHUFFLE_ENABLED] = shuffleEnabled
            prefs[Keys.REPEAT_MODE] = repeatMode.name
            if (lastTrackId != null) {
                prefs[Keys.LAST_TRACK_ID] = lastTrackId
            } else {
                prefs.remove(Keys.LAST_TRACK_ID)
            }
        }
    }

    suspend fun setGuestMode(enabled: Boolean) {
        context.musicDataStore.edit { prefs ->
            prefs[Keys.IS_GUEST_MODE] = enabled
        }
    }

    suspend fun clearUserSessionData() {
        context.musicDataStore.edit { prefs ->
            prefs.remove(Keys.FAVORITE_IDS)
            prefs.remove(Keys.FAVORITE_ONLINE_TRACKS)
            prefs.remove(Keys.SHUFFLE_ENABLED)
            prefs.remove(Keys.REPEAT_MODE)
            prefs.remove(Keys.LAST_TRACK_ID)
            prefs.remove(Keys.IS_GUEST_MODE)
        }
    }
}
