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
    val appliedVolumeFraction: Float = 1.0f
) {
    val isActiveForDriving: Boolean
        get() = isEnabled && (!onlyWhenAndroidAutoConnected || isAndroidAutoConnected)

    val appliedVolumePercent: Int
        get() = (appliedVolumeFraction * 100f).roundToInt().coerceIn(0, 100)

    val speedRoundedKmh: Int
        get() = currentSpeedKmh.roundToInt().coerceAtLeast(0)
}

object SpeedVolumeCalculator {
    private const val MIN_COMPUTED_TIME_DELTA_SEC = 0.5f
    private const val MAX_COMPUTED_TIME_DELTA_SEC = 10.0f
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
                val accuracyOk = !location.hasAccuracy() || location.accuracy <= 40f
                if (accuracyOk) {
                    val meters = previousLocation.distanceTo(location)
                    val kmh = (meters / deltaSeconds) * 3.6f
                    return kmh.coerceIn(0f, MAX_REASONABLE_SPEED_KMH)
                }
            }
        }
        return null
    }
}

class AutoSpeedVolumeManager(
    context: Context,
    private val preferences: MusicPreferences
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        AutoSpeedVolumeState(hasLocationPermission = hasLocationPermission(appContext))
    )
    val state: StateFlow<AutoSpeedVolumeState> = _state.asStateFlow()

    private var attachedPlayer: Player? = null
    private var lastLocation: Location? = null
    private var hasSpeedReading = false
    private var isLocationRegistered = false
    private var isConnectionMonitoringStarted = false
    private var isCarProviderConnected = false
    private var isAutoControllerConnected = false

    private val carConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshCarProviderConnectionState()
        }
    }
    private var isReceiverRegistered = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
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
            preferences.prefsFlow.collect { prefs ->
                _state.update {
                    it.copy(
                        isEnabled = prefs.speedVolumeEnabled,
                        sensitivity = prefs.speedVolumeSensitivity,
                        onlyWhenAndroidAutoConnected = prefs.onlyWhenAndroidAutoConnected
                    )
                }
                evaluateMonitoringAndVolume()
            }
        }
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
        attachedPlayer?.volume = cleanVolume
        _state.update {
            it.copy(
                currentSpeedKmh = cleanSpeed,
                appliedVolumeFraction = cleanVolume,
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
        _state.update {
            it.copy(
                currentSpeedKmh = smoothedSpeed,
                appliedVolumeFraction = nextVolume
            )
        }
        attachedPlayer?.volume = nextVolume
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
            _state.value = current.copy(
                isMonitoringSpeed = isLocationRegistered || SpeedVolumeService.isServiceRunning,
                isBackgroundServiceRunning = SpeedVolumeService.isServiceRunning,
                appliedVolumeFraction = targetVolume
            )
            attachedPlayer?.volume = targetVolume
        } else {
            stopLocationUpdates()
            SpeedVolumeService.stop(appContext)
            _state.value = current.copy(
                isMonitoringSpeed = false,
                isBackgroundServiceRunning = false,
                currentSpeedKmh = if (!current.isActiveForDriving) 0f else current.currentSpeedKmh,
                appliedVolumeFraction = 1.0f
            )
            attachedPlayer?.volume = 1.0f
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isLocationRegistered || !hasLocationPermission(appContext)) return
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        val providers = locationManager.allProviders
        val chosenProvider = when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return

        runCatching {
            locationManager.requestLocationUpdates(
                chosenProvider,
                LOCATION_MIN_TIME_MS,
                LOCATION_MIN_DISTANCE_M,
                locationListener,
                Looper.getMainLooper()
            )
            isLocationRegistered = true
        }.onFailure {
            isLocationRegistered = false
        }
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
        private const val LOCATION_MIN_DISTANCE_M = 1.0f

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
