package com.example.headunit

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hurDataStore: DataStore<Preferences> by preferencesDataStore(name = "hur_headunit_prefs")

class HeadunitPreferences(private val context: Context) {

    private object Keys {
        val CONNECTION_MODE = stringPreferencesKey("hur_connection_mode")
        val CLIENT_HOST_IP = stringPreferencesKey("hur_client_host_ip")
        val PORT = intPreferencesKey("hur_port")
        val RESOLUTION = stringPreferencesKey("hur_resolution")
        val DPI = intPreferencesKey("hur_dpi")
        val TARGET_FPS = intPreferencesKey("hur_target_fps")
        val CARPLAY_THEME = stringPreferencesKey("carplay_theme")
        val DOCK_SIDE = stringPreferencesKey("carplay_dock_side")
        val HARDWARE_DECODE = booleanPreferencesKey("hur_hw_decode")
        val MIC_PASSTHROUGH = booleanPreferencesKey("hur_mic_passthrough")
        val KEEP_SCREEN_ON = booleanPreferencesKey("hur_keep_screen_on")
        val IMMERSIVE_MODE = booleanPreferencesKey("hur_immersive_mode")
    }

    val settingsFlow: Flow<HeadunitSettings> = context.hurDataStore.data.map { prefs ->
        val mode = prefs[Keys.CONNECTION_MODE]?.let {
            runCatching { HurConnectionMode.valueOf(it) }.getOrNull()
        } ?: HurConnectionMode.WIFI_SERVER

        val resolution = prefs[Keys.RESOLUTION]?.let {
            runCatching { HurResolution.valueOf(it) }.getOrNull()
        } ?: HurResolution.HD_720P

        val theme = prefs[Keys.CARPLAY_THEME]?.let {
            runCatching { CarPlayThemeMode.valueOf(it) }.getOrNull()
        } ?: CarPlayThemeMode.NIGHT_GLASS

        val dockSide = prefs[Keys.DOCK_SIDE]?.let {
            runCatching { CarPlayDockSide.valueOf(it) }.getOrNull()
        } ?: CarPlayDockSide.LEFT

        HeadunitSettings(
            connectionMode = mode,
            clientHostIp = prefs[Keys.CLIENT_HOST_IP] ?: "127.0.0.1",
            port = prefs[Keys.PORT] ?: 5277,
            resolution = resolution,
            dpi = (prefs[Keys.DPI] ?: 160).coerceIn(120, 320),
            targetFps = if ((prefs[Keys.TARGET_FPS] ?: 60) == 30) 30 else 60,
            carPlayTheme = theme,
            dockSide = dockSide,
            hardwareVideoDecode = prefs[Keys.HARDWARE_DECODE] ?: true,
            micPassthrough = prefs[Keys.MIC_PASSTHROUGH] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            immersiveMode = prefs[Keys.IMMERSIVE_MODE] ?: true
        )
    }

    suspend fun updateSettings(transform: (HeadunitSettings) -> HeadunitSettings) {
        context.hurDataStore.edit { prefs ->
            val current = HeadunitSettings(
                connectionMode = prefs[Keys.CONNECTION_MODE]?.let {
                    runCatching { HurConnectionMode.valueOf(it) }.getOrNull()
                } ?: HurConnectionMode.WIFI_SERVER,
                clientHostIp = prefs[Keys.CLIENT_HOST_IP] ?: "127.0.0.1",
                port = prefs[Keys.PORT] ?: 5277,
                resolution = prefs[Keys.RESOLUTION]?.let {
                    runCatching { HurResolution.valueOf(it) }.getOrNull()
                } ?: HurResolution.HD_720P,
                dpi = (prefs[Keys.DPI] ?: 160).coerceIn(120, 320),
                targetFps = if ((prefs[Keys.TARGET_FPS] ?: 60) == 30) 30 else 60,
                carPlayTheme = prefs[Keys.CARPLAY_THEME]?.let {
                    runCatching { CarPlayThemeMode.valueOf(it) }.getOrNull()
                } ?: CarPlayThemeMode.NIGHT_GLASS,
                dockSide = prefs[Keys.DOCK_SIDE]?.let {
                    runCatching { CarPlayDockSide.valueOf(it) }.getOrNull()
                } ?: CarPlayDockSide.LEFT,
                hardwareVideoDecode = prefs[Keys.HARDWARE_DECODE] ?: true,
                micPassthrough = prefs[Keys.MIC_PASSTHROUGH] ?: false,
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
                immersiveMode = prefs[Keys.IMMERSIVE_MODE] ?: true
            )
            val updated = transform(current)
            prefs[Keys.CONNECTION_MODE] = updated.connectionMode.name
            prefs[Keys.CLIENT_HOST_IP] = updated.clientHostIp
            prefs[Keys.PORT] = updated.port
            prefs[Keys.RESOLUTION] = updated.resolution.name
            prefs[Keys.DPI] = updated.dpi
            prefs[Keys.TARGET_FPS] = updated.targetFps
            prefs[Keys.CARPLAY_THEME] = updated.carPlayTheme.name
            prefs[Keys.DOCK_SIDE] = updated.dockSide.name
            prefs[Keys.HARDWARE_DECODE] = updated.hardwareVideoDecode
            prefs[Keys.MIC_PASSTHROUGH] = updated.micPassthrough
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.IMMERSIVE_MODE] = updated.immersiveMode
        }
    }
}
