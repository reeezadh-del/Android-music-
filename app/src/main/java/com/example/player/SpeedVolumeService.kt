package com.example.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.AvaMusicApplication
import com.example.MainActivity
import com.example.R
import com.example.diagnostics.CrashReporter
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Background foreground-eligible service that monitors vehicle speed via [LocationManager]
 * and adjusts playback audio volume via the active [MusicPlaybackService] MediaSession
 * (and optional hardware stream scaling when appropriate) as vehicle speed increases.
 */
@OptIn(UnstableApi::class)
class SpeedVolumeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var locationManager: LocationManager? = null
    private var isLocationRegistered = false
    private var lastLocation: Location? = null
    private var hasSpeedReading = false

    private var currentSpeedKmh = 0f
    private var currentVolumeFraction = 1.0f
    private var isEnabled = true
    private var onlyWhenAndroidAutoConnected = false
    private var isAndroidAutoConnected = false
    private var sensitivity = SpeedVolumeSensitivity.BALANCED

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationSample(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        ensureNotificationChannel()
        connectToMediaSession()
        observePreferencesAndAutoState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        val app = application as? AvaMusicApplication
        val autoState = app?.autoSpeedVolumeManager?.state?.value
        if (autoState != null) {
            isEnabled = autoState.isEnabled
            onlyWhenAndroidAutoConnected = autoState.onlyWhenAndroidAutoConnected
            isAndroidAutoConnected = autoState.isAndroidAutoConnected
            sensitivity = autoState.sensitivity
        }

        if (!AutoSpeedVolumeManager.hasLocationPermission(this) || !shouldActivelyMonitor()) {
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        promoteToForegroundSafely()
        startLocationUpdates()
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopMonitoringAndSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        stopLocationUpdates()
        dispatchVolumeCommandToSession(speedKmh = 0f, volumeFraction = 1.0f)
        (application as? AvaMusicApplication)?.autoSpeedVolumeManager?.onServiceMonitoringChanged(
            isRunning = false
        )
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        mediaController = null
        serviceScope.cancel()
        super.onDestroy()
    }

    @VisibleForTesting
    internal fun handleLocationSample(location: Location) {
        val rawKmh = SpeedVolumeCalculator.extractSpeedKmh(location, lastLocation)
        lastLocation = location
        if (rawKmh != null) {
            handleSpeedSampleKmh(rawKmh)
        }
    }

    @VisibleForTesting
    internal fun handleSpeedSampleKmh(rawSpeedKmh: Float) {
        if (!shouldActivelyMonitor()) {
            applyTargetVolume(speedKmh = 0f, volumeFraction = 1.0f)
            return
        }

        val smoothedSpeed = SpeedVolumeCalculator.smoothSpeedKmh(
            previousSmoothedKmh = currentSpeedKmh,
            rawSpeedKmh = rawSpeedKmh,
            hasPreviousReading = hasSpeedReading
        )
        hasSpeedReading = true

        val targetVolume = SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = smoothedSpeed,
            sensitivity = sensitivity,
            isAndroidAutoConnected = isAndroidAutoConnected,
            isEnabled = isEnabled,
            onlyWhenAndroidAutoConnected = onlyWhenAndroidAutoConnected
        )
        val steppedVolume = SpeedVolumeCalculator.smoothVolumeStep(currentVolumeFraction, targetVolume)
        applyTargetVolume(speedKmh = smoothedSpeed, volumeFraction = steppedVolume)
    }

    @VisibleForTesting
    internal fun currentComputedVolume(): Float = currentVolumeFraction

    @VisibleForTesting
    internal fun currentComputedSpeed(): Float = currentSpeedKmh

    private fun shouldActivelyMonitor(): Boolean {
        return isEnabled && (!onlyWhenAndroidAutoConnected || isAndroidAutoConnected)
    }

    private fun observePreferencesAndAutoState() {
        val app = application as? AvaMusicApplication ?: return
        serviceScope.launch {
            app.preferences.prefsFlow.collectLatest { prefs ->
                isEnabled = prefs.speedVolumeEnabled
                sensitivity = prefs.speedVolumeSensitivity
                onlyWhenAndroidAutoConnected = prefs.onlyWhenAndroidAutoConnected
                reconcileActiveMonitoringState()
            }
        }
        serviceScope.launch {
            app.autoSpeedVolumeManager.state.collectLatest { state ->
                isAndroidAutoConnected = state.isAndroidAutoConnected
                reconcileActiveMonitoringState()
            }
        }
    }

    private fun reconcileActiveMonitoringState() {
        if (!AutoSpeedVolumeManager.hasLocationPermission(this) || !shouldActivelyMonitor()) {
            stopMonitoringAndSelf()
            return
        }
        val targetVolume = SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = currentSpeedKmh,
            sensitivity = sensitivity,
            isAndroidAutoConnected = isAndroidAutoConnected,
            isEnabled = isEnabled,
            onlyWhenAndroidAutoConnected = onlyWhenAndroidAutoConnected
        )
        applyTargetVolume(speedKmh = currentSpeedKmh, volumeFraction = targetVolume)
    }

    private fun applyTargetVolume(speedKmh: Float, volumeFraction: Float) {
        currentSpeedKmh = speedKmh
        currentVolumeFraction = volumeFraction

        dispatchVolumeCommandToSession(speedKmh = speedKmh, volumeFraction = volumeFraction)

        // Ensure manager state stays synchronized even if the MediaController is still connecting.
        (application as? AvaMusicApplication)?.autoSpeedVolumeManager?.onServiceVolumeApplied(
            speedKmh = speedKmh,
            volumeFraction = volumeFraction
        )
        updateNotificationProgress()
    }

    private fun connectToMediaSession() {
        runCatching {
            val token = SessionToken(
                this,
                ComponentName(this, MusicPlaybackService::class.java)
            )
            val future = MediaController.Builder(this, token).buildAsync()
            mediaControllerFuture = future
            future.addListener(
                {
                    runCatching { future.get() }.onSuccess { controller ->
                        mediaController = controller
                        dispatchVolumeCommandToSession(
                            speedKmh = currentSpeedKmh,
                            volumeFraction = currentVolumeFraction
                        )
                    }.onFailure { err ->
                        CrashReporter.recordNonFatal("SPEED_SERVICE_SESSION_FUTURE", err, context = this)
                    }
                },
                MoreExecutors.directExecutor()
            )
        }.onFailure { err ->
            CrashReporter.recordNonFatal("SPEED_SERVICE_SESSION_BIND", err, context = this)
        }
    }

    private fun dispatchVolumeCommandToSession(speedKmh: Float, volumeFraction: Float) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putFloat(MusicPlaybackService.EXTRA_SPEED_KMH, speedKmh)
            putFloat(MusicPlaybackService.EXTRA_VOLUME_FRACTION, volumeFraction)
        }
        runCatching {
            controller.sendCustomCommand(
                SessionCommand(MusicPlaybackService.ACTION_SET_SPEED_VOLUME, Bundle.EMPTY),
                args
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isLocationRegistered || !AutoSpeedVolumeManager.hasLocationPermission(this)) return
        val manager = (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            ?.also { locationManager = it }
            ?: return

        val providers = manager.allProviders
        val chosenProvider = when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return

        runCatching {
            manager.requestLocationUpdates(
                chosenProvider,
                LOCATION_MIN_TIME_MS,
                LOCATION_MIN_DISTANCE_M,
                locationListener,
                Looper.getMainLooper()
            )
            isLocationRegistered = true
            (application as? AvaMusicApplication)?.autoSpeedVolumeManager?.onServiceMonitoringChanged(
                isRunning = true
            )
        }.onFailure { err ->
            isLocationRegistered = false
            CrashReporter.recordNonFatal("SPEED_LOCATION_REGISTER", err, context = this)
        }
    }

    private fun stopLocationUpdates() {
        if (!isLocationRegistered) return
        val manager = locationManager ?: (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
        if (manager != null) {
            runCatching { manager.removeUpdates(locationListener) }
        }
        isLocationRegistered = false
    }

    private fun stopMonitoringAndSelf() {
        stopLocationUpdates()
        applyTargetVolume(speedKmh = 0f, volumeFraction = 1.0f)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteToForegroundSafely() {
        val notification = buildNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }.onFailure { err ->
            CrashReporter.recordNonFatal("SPEED_FGS_START", err, context = this)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.speed_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.speed_service_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateNotificationProgress() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, SpeedVolumeService::class.java).setAction(ACTION_STOP_MONITORING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val speedKmh = currentSpeedKmh.roundToInt().coerceAtLeast(0)
        val volPct = (currentVolumeFraction * 100f).roundToInt().coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.speed_service_notif_title))
            .setContentText(getString(R.string.speed_service_notif_body, speedKmh, volPct))
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.speed_service_notif_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START_MONITORING = "com.example.player.ACTION_START_SPEED_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.player.ACTION_STOP_SPEED_MONITORING"
        private const val CHANNEL_ID = "ava_speed_volume_channel"
        private const val NOTIFICATION_ID = 4207
        private const val LOCATION_MIN_TIME_MS = 1_000L
        private const val LOCATION_MIN_DISTANCE_M = 1.0f

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context): Boolean {
            if (!AutoSpeedVolumeManager.hasLocationPermission(context)) return false
            val intent = Intent(context, SpeedVolumeService::class.java)
                .setAction(ACTION_START_MONITORING)
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrElse { err ->
                CrashReporter.recordNonFatal("SPEED_SERVICE_START", err, context = context)
                false
            }
        }

        fun stop(context: Context) {
            if (!isServiceRunning) return
            runCatching {
                context.stopService(Intent(context, SpeedVolumeService::class.java))
            }
        }
    }
}
