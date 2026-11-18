package com.example.player

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpeedVolumeSensitivity(
    val labelFa: String,
    val descriptionFa: String,
    val minVolume: Float,
    val minSpeedKmh: Float,
    val maxSpeedKmh: Float
) {
    MILD(
        labelFa = "ملایم",
        descriptionFa = "تغییر نرم صدا (۶۵٪ تا ۱۰۰٪)",
        minVolume = 0.65f,
        minSpeedKmh = 15f,
        maxSpeedKmh = 110f
    ),
    BALANCED(
        labelFa = "متعادل",
        descriptionFa = "تنظیم استاندارد داخل خودرو (۵۰٪ تا ۱۰۰٪)",
        minVolume = 0.50f,
        minSpeedKmh = 12f,
        maxSpeedKmh = 105f
    ),
    STRONG(
        labelFa = "زیاد",
        descriptionFa = "افزایش محسوس در سرعت بالا (۳۵٪ تا ۱۰۰٪)",
        minVolume = 0.35f,
        minSpeedKmh = 10f,
        maxSpeedKmh = 100f
    )
}

data class AutoSpeedVolumeState(
    val isEnabled: Boolean = true,
    val onlyWhenAndroidAutoConnected: Boolean = false,
    val sensitivity: SpeedVolumeSensitivity = SpeedVolumeSensitivity.BALANCED,
    val isAndroidAutoConnected: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isMonitoringSpeed: Boolean = false,
    val isBackgroundServiceRunning: Boolean = false,
    val currentSpeedKmh: Float = 0f,
    val appliedVolumeFraction: Float = 1.0f,
    val streamVolumeIndex: Int = 0,
    val streamVolumeMax: Int = 15
) {
    val isActiveForDriving: Boolean
        get() = isEnabled && (!onlyWhenAndroidAutoConnected || isAndroidAutoConnected)

    val appliedVolumePercent: Int
        get() = (appliedVolumeFraction * 100f).roundToInt().coerceIn(0, 100)

    val speedRoundedKmh: Int
        get() = currentSpeedKmh.roundToInt().coerceAtLeast(0)
}

object SpeedVolumeCalculator {
    private const val MIN_COMPUTED_TIME_DELTA_SEC = 0.4f
    private const val MAX_COMPUTED_TIME_DELTA_SEC = 30.0f
    private const val MAX_REASONABLE_SPEED_KMH = 240f
    private const val EMA_ALPHA = 0.35f
    private const val VOLUME_STEP_ALPHA = 0.45f

    fun smoothSpeedKmh(
        previousSmoothedKmh: Float,
        rawSpeedKmh: Float,
        hasPreviousReading: Boolean
    ): Float {
        val clampedRaw = rawSpeedKmh.coerceIn(0f, MAX_REASONABLE_SPEED_KMH)
        if (!hasPreviousReading) return clampedRaw
        val blended = (EMA_ALPHA * clampedRaw) + ((1f - EMA_ALPHA) * previousSmoothedKmh)
        return if (blended < 1.5f && clampedRaw < 1.5f) 0f else blended
    }

    fun computeTargetVolume(
        speedKmh: Float,
        sensitivity: SpeedVolumeSensitivity,
        isAndroidAutoConnected: Boolean,
        isEnabled: Boolean,
        onlyWhenAndroidAutoConnected: Boolean = true
    ): Float {
        if (!isEnabled || (onlyWhenAndroidAutoConnected && !isAndroidAutoConnected)) {
            return 1.0f
        }
        val cleanSpeed = speedKmh.coerceAtLeast(0f)
        if (cleanSpeed <= sensitivity.minSpeedKmh) {
            return sensitivity.minVolume
        }
        if (cleanSpeed >= sensitivity.maxSpeedKmh) {
            return 1.0f
        }
        val span = (sensitivity.maxSpeedKmh - sensitivity.minSpeedKmh).coerceAtLeast(1f)
        val normalized = ((cleanSpeed - sensitivity.minSpeedKmh) / span).coerceIn(0f, 1f)
        // Mild ease-in-out curve so low-speed jitter feels calm while highway acceleration boosts steadily.
        val curved = normalized * normalized * (3f - 2f * normalized)
        return (sensitivity.minVolume + (1.0f - sensitivity.minVolume) * curved).coerceIn(0.1f, 1.0f)
    }

    fun smoothVolumeStep(currentVolume: Float, targetVolume: Float): Float {
        val clampedTarget = targetVolume.coerceIn(0.1f, 1.0f)
        val delta = kotlin.math.abs(clampedTarget - currentVolume)
        if (delta <= 0.015f) return clampedTarget
        return (currentVolume + (clampedTarget - currentVolume) * VOLUME_STEP_ALPHA).coerceIn(0.1f, 1.0f)
    }

    fun extractSpeedKmh(location: Location, previousLocation: Location?): Float? {
        if (location.hasSpeed() && location.speed >= 0f) {
            return (location.speed * 3.6f).coerceIn(0f, MAX_REASONABLE_SPEED_KMH)
        }
        if (previousLocation != null && location.time > previousLocation.time) {
            val deltaSeconds = (location.time - previousLocation.time) / 1000f
            if (deltaSeconds in MIN_COMPUTED_TIME_DELTA_SEC..MAX_COMPUTED_TIME_DELTA_SEC) {
                val accuracy = if (location.hasAccuracy()) location.accuracy else 20f
                if (accuracy <= 80f) {
                    val meters = previousLocation.distanceTo(location)
                    val effectiveMeters = if (accuracy <= 40f || meters > accuracy * 0.7f) meters else 0f
                    val kmh = (effectiveMeters / deltaSeconds) * 3.6f
                    return kmh.coerceIn(0f, MAX_REASONABLE_SPEED_KMH)
                }
            }
        }
        return null
    }
}

private data class SpeedPrefsConfig(
    val isEnabled: Boolean,
    val sensitivity: SpeedVolumeSensitivity,
    val onlyWhenAndroidAutoConnected: Boolean
)

class AutoSpeedVolumeManager(
    context: Context,
    private val preferences: MusicPreferences
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        AutoSpeedVolumeState(
            hasLocationPermission = hasLocationPermission(appContext),
            streamVolumeIndex = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0,
            streamVolumeMax = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        )
    )
    val state: StateFlow<AutoSpeedVolumeState> = _state.asStateFlow()

    private var attachedPlayer: Player? = null
    private var lastLocation: Location? = null
    private var lastGpsSpeedTimeMs: Long = 0L
    private var hasSpeedReading = false
    private var isLocationRegistered = false
    private var isConnectionMonitoringStarted = false
    private var isCarProviderConnected = false
    private var isAutoControllerConnected = false

    private var baselineStreamVolume: Int? = null
    private var lastAppliedStreamIndex: Int? = null

    private val carConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshCarProviderConnectionState()
        }
    }
    private var isReceiverRegistered = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isLocationNoiseFromFallbackProvider(location)) return
            val rawKmh = SpeedVolumeCalculator.extractSpeedKmh(location, lastLocation)
            lastLocation = location
            if (rawKmh != null) {
                updateSpeedReading(rawKmh)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    init {
        scope.launch {
            preferences.prefsFlow
                .map { prefs ->
                    SpeedPrefsConfig(
                        isEnabled = prefs.speedVolumeEnabled,
                        sensitivity = prefs.speedVolumeSensitivity,
                        onlyWhenAndroidAutoConnected = prefs.onlyWhenAndroidAutoConnected
                    )
                }
                .distinctUntilChanged()
                .collect { cfg ->
                    _state.update {
                        it.copy(
                            isEnabled = cfg.isEnabled,
                            sensitivity = cfg.sensitivity,
                            onlyWhenAndroidAutoConnected = cfg.onlyWhenAndroidAutoConnected
                        )
                    }
                    evaluateMonitoringAndVolume()
                }
        }
    }

    private fun isLocationNoiseFromFallbackProvider(location: Location): Boolean {
        val now = System.currentTimeMillis()
        if (location.provider == LocationManager.GPS_PROVIDER || location.hasSpeed()) {
            lastGpsSpeedTimeMs = now
            return false
        }
        return (now - lastGpsSpeedTimeMs) < 5_000L
    }

    fun startMonitoringConnection() {
        if (isConnectionMonitoringStarted) return
        isConnectionMonitoringStarted = true

        if (MusicPlaybackService.hasCarConnectionProvider(appContext)) {
            if (!isReceiverRegistered) {
                val filter = IntentFilter(ACTION_CAR_CONNECTION_UPDATED)
                runCatching {
                    ContextCompat.registerReceiver(
                        appContext,
                        carConnectionReceiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                    isReceiverRegistered = true
                }
            }
            refreshCarProviderConnectionState()
        }
    }

    fun stopMonitoringConnection() {
        if (isReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(carConnectionReceiver) }
            isReceiverRegistered = false
        }
        isConnectionMonitoringStarted = false
        stopLocationUpdates()
    }

    fun attachPlayer(player: Player) {
        attachedPlayer = player
        evaluateMonitoringAndVolume()
    }

    fun detachPlayer(player: Player) {
        if (attachedPlayer === player) {
            player.volume = 1.0f
            attachedPlayer = null
        }
        stopLocationUpdates()
        restoreBaselineVolume()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun updateConnectedControllers(session: MediaSession) {
        val hasAutoController = session.connectedControllers.any { controller ->
            session.isAutomotiveController(controller) ||
                controller.packageName == ANDROID_AUTO_PACKAGE
        }
        if (isAutoControllerConnected != hasAutoController) {
            isAutoControllerConnected = hasAutoController
            syncCombinedConnectionState()
        }
    }

    fun refreshPermissionState() {
        val granted = hasLocationPermission(appContext)
        _state.update { it.copy(hasLocationPermission = granted) }
        evaluateMonitoringAndVolume()
    }

    fun syncBackgroundServiceFromForeground() {
        evaluateMonitoringAndVolume(allowForegroundServiceStart = true)
    }

    fun onServiceMonitoringChanged(isRunning: Boolean) {
        if (isRunning) {
            stopLocationUpdates()
        }
        _state.update {
            it.copy(
                isBackgroundServiceRunning = isRunning,
                isMonitoringSpeed = isRunning || isLocationRegistered
            )
        }
    }

    fun onServiceVolumeApplied(speedKmh: Float, volumeFraction: Float) {
        val cleanSpeed = speedKmh.coerceAtLeast(0f)
        val cleanVolume = volumeFraction.coerceIn(0.1f, 1.0f)
        val current = _state.value
        if (current.isActiveForDriving && current.hasLocationPermission) {
            applyVolumeToOutputs(cleanVolume)
        } else {
            restoreBaselineVolume()
        }
        val (volIdx, volMax) = readCurrentStreamVolumePair()
        _state.update {
            it.copy(
                currentSpeedKmh = cleanSpeed,
                appliedVolumeFraction = cleanVolume,
                streamVolumeIndex = volIdx,
                streamVolumeMax = volMax,
                isBackgroundServiceRunning = SpeedVolumeService.isServiceRunning,
                isMonitoringSpeed = SpeedVolumeService.isServiceRunning || isLocationRegistered
            )
        }
    }

    fun refreshCarProviderConnectionState() {
        if (!MusicPlaybackService.hasCarConnectionProvider(appContext)) {
            if (isCarProviderConnected) {
                isCarProviderConnected = false
                syncCombinedConnectionState()
            }
            return
        }

        val stateCode = runCatching {
            appContext.contentResolver.query(
                CAR_CONNECTION_QUERY_URI,
                arrayOf(CAR_CONNECTION_STATE_COLUMN),
                null,
                null,
                null
            )?.use { cursor ->
                val colIdx = cursor.getColumnIndex(CAR_CONNECTION_STATE_COLUMN)
                if (colIdx != -1 && cursor.moveToNext()) {
                    cursor.getInt(colIdx)
                } else {
                    CONNECTION_TYPE_NOT_CONNECTED
                }
            } ?: CONNECTION_TYPE_NOT_CONNECTED
        }.getOrDefault(CONNECTION_TYPE_NOT_CONNECTED)

        val connected = stateCode == CONNECTION_TYPE_PROJECTION || stateCode == CONNECTION_TYPE_NATIVE
        if (isCarProviderConnected != connected) {
            isCarProviderConnected = connected
            syncCombinedConnectionState()
        }
    }

    @VisibleForTesting
    internal fun onSpeedSampleForTest(rawSpeedKmh: Float) {
        updateSpeedReading(rawSpeedKmh)
    }

    @VisibleForTesting
    internal fun setCarConnectionOverrideForTest(connected: Boolean) {
        isCarProviderConnected = connected
        syncCombinedConnectionState()
    }

    private fun syncCombinedConnectionState() {
        val combinedConnected = isCarProviderConnected || isAutoControllerConnected
        _state.update {
            if (combinedConnected) {
                it.copy(isAndroidAutoConnected = true)
            } else {
                val shouldReset = it.onlyWhenAndroidAutoConnected
                if (shouldReset) {
                    hasSpeedReading = false
                    lastLocation = null
                }
                it.copy(
                    isAndroidAutoConnected = false,
                    currentSpeedKmh = if (shouldReset) 0f else it.currentSpeedKmh
                )
            }
        }
        evaluateMonitoringAndVolume(allowForegroundServiceStart = combinedConnected)
    }

    private fun updateSpeedReading(rawSpeedKmh: Float) {
        val current = _state.value
        if (!current.isActiveForDriving) return
        val smoothedSpeed = SpeedVolumeCalculator.smoothSpeedKmh(
            previousSmoothedKmh = current.currentSpeedKmh,
            rawSpeedKmh = rawSpeedKmh,
            hasPreviousReading = hasSpeedReading
        )
        hasSpeedReading = true
        val targetVolume = SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = smoothedSpeed,
            sensitivity = current.sensitivity,
            isAndroidAutoConnected = current.isAndroidAutoConnected,
            isEnabled = current.isEnabled,
            onlyWhenAndroidAutoConnected = current.onlyWhenAndroidAutoConnected
        )
        val nextVolume = SpeedVolumeCalculator.smoothVolumeStep(current.appliedVolumeFraction, targetVolume)
        applyVolumeToOutputs(nextVolume)
        val (volIdx, volMax) = readCurrentStreamVolumePair()
        _state.update {
            it.copy(
                currentSpeedKmh = smoothedSpeed,
                appliedVolumeFraction = nextVolume,
                streamVolumeIndex = volIdx,
                streamVolumeMax = volMax
            )
        }
    }

    private fun evaluateMonitoringAndVolume(allowForegroundServiceStart: Boolean = false) {
        val granted = hasLocationPermission(appContext)
        val current = _state.value.let {
            if (it.hasLocationPermission != granted) it.copy(hasLocationPermission = granted) else it
        }
        val shouldTrack = current.isActiveForDriving && granted

        if (shouldTrack) {
            if (allowForegroundServiceStart && !SpeedVolumeService.isServiceRunning) {
                SpeedVolumeService.start(appContext)
            }
            if (!SpeedVolumeService.isServiceRunning) {
                startLocationUpdates()
            } else {
                stopLocationUpdates()
            }
            val targetVolume = SpeedVolumeCalculator.computeTargetVolume(
                speedKmh = current.currentSpeedKmh,
                sensitivity = current.sensitivity,
                isAndroidAutoConnected = current.isAndroidAutoConnected,
                isEnabled = current.isEnabled,
                onlyWhenAndroidAutoConnected = current.onlyWhenAndroidAutoConnected
            )
            applyVolumeToOutputs(targetVolume)
            val (volIdx, volMax) = readCurrentStreamVolumePair()
            _state.value = current.copy(
                isMonitoringSpeed = isLocationRegistered || SpeedVolumeService.isServiceRunning,
                isBackgroundServiceRunning = SpeedVolumeService.isServiceRunning,
                appliedVolumeFraction = targetVolume,
                streamVolumeIndex = volIdx,
                streamVolumeMax = volMax
            )
        } else {
            stopLocationUpdates()
            SpeedVolumeService.stop(appContext)
            restoreBaselineVolume()
            val (volIdx, volMax) = readCurrentStreamVolumePair()
            _state.value = current.copy(
                isMonitoringSpeed = false,
                isBackgroundServiceRunning = false,
                currentSpeedKmh = if (!current.isActiveForDriving) 0f else current.currentSpeedKmh,
                appliedVolumeFraction = 1.0f,
                streamVolumeIndex = volIdx,
                streamVolumeMax = volMax
            )
        }
    }

    private fun readCurrentStreamVolumePair(): Pair<Int, Int> {
        val am = audioManager ?: return 0 to 15
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVol)
        return curVol to maxVol
    }

    private fun applyVolumeToOutputs(volumeFraction: Float) {
        val cleanFraction = volumeFraction.coerceIn(0.1f, 1.0f)
        val am = audioManager
        if (am != null && !am.isVolumeFixed) {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                if (baselineStreamVolume == null) {
                    baselineStreamVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
                val minAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    am.getStreamMinVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                } else {
                    1
                }.coerceAtMost(maxVol)
                val targetIndex = (cleanFraction * maxVol).roundToInt().coerceIn(minAllowed, maxVol)
                if (lastAppliedStreamIndex != targetIndex) {
                    runCatching {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
                    }
                    lastAppliedStreamIndex = targetIndex
                }
                // Keep ExoPlayer software gain at unity so hardware STREAM_MUSIC controls full phone loudness cleanly.
                attachedPlayer?.volume = 1.0f
                return
            }
        }
        // Fallback for fixed-volume car head units / Android Auto sinks where STREAM_MUSIC cannot change.
        attachedPlayer?.volume = cleanFraction
    }

    internal fun restoreBaselineVolume() {
        attachedPlayer?.volume = 1.0f
        val baseline = baselineStreamVolume
        val am = audioManager
        if (baseline != null && am != null && !am.isVolumeFixed) {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            runCatching {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, baseline.coerceIn(0, maxVol), 0)
            }
        }
        baselineStreamVolume = null
        lastAppliedStreamIndex = null
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isLocationRegistered || !hasLocationPermission(appContext)) return
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        val providersToTry = selectBestLocationProviders(locationManager)
        if (providersToTry.isEmpty()) return

        var registeredAny = false
        for (provider in providersToTry) {
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    LOCATION_MIN_TIME_MS,
                    LOCATION_MIN_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper()
                )
                registeredAny = true
            }
        }
        isLocationRegistered = registeredAny
    }

    private fun stopLocationUpdates() {
        if (!isLocationRegistered) return
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            runCatching { locationManager.removeUpdates(locationListener) }
        }
        isLocationRegistered = false
    }

    companion object {
        private const val ACTION_CAR_CONNECTION_UPDATED =
            "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"
        private const val CAR_CONNECTION_STATE_COLUMN = "CarConnectionState"
        private val CAR_CONNECTION_QUERY_URI: Uri =
            Uri.parse("content://${MusicPlaybackService.CAR_CONNECTION_AUTHORITY}")
        private const val CONNECTION_TYPE_NOT_CONNECTED = 0
        private const val CONNECTION_TYPE_NATIVE = 1
        private const val CONNECTION_TYPE_PROJECTION = 2
        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
        private const val LOCATION_MIN_TIME_MS = 1_000L
        private const val LOCATION_MIN_DISTANCE_M = 0.0f

        internal fun selectBestLocationProviders(locationManager: LocationManager): List<String> {
            val all = locationManager.allProviders
            val candidates = buildList {
                if (LocationManager.GPS_PROVIDER in all &&
                    runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
                ) {
                    add(LocationManager.GPS_PROVIDER)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    LocationManager.FUSED_PROVIDER in all &&
                    runCatching { locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
                ) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                if (isEmpty() && LocationManager.NETWORK_PROVIDER in all &&
                    runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
                ) {
                    add(LocationManager.NETWORK_PROVIDER)
                }
                if (isEmpty()) {
                    when {
                        LocationManager.GPS_PROVIDER in all -> add(LocationManager.GPS_PROVIDER)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            LocationManager.FUSED_PROVIDER in all -> add(LocationManager.FUSED_PROVIDER)
                        LocationManager.NETWORK_PROVIDER in all -> add(LocationManager.NETWORK_PROVIDER)
                    }
                }
            }
            return candidates.distinct()
        }

        fun hasLocationPermission(context: Context): Boolean {
            val fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fineGranted || coarseGranted
        }
    }
}
