package com.example.headunit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Provides real device telemetry for the Apple CarPlay Headunit UI:
 * - Installed launchable Android apps (Maps, Waze, Neshan, Balad, Spotify, Phone, Messages, etc.)
 * - Real GPS speed, bearing, and coordinates via LocationManager (when permission granted)
 * - Real battery percentage
 */
class DeviceCarDataProvider(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _gpsTelemetry = MutableStateFlow(GpsTelemetry())
    val gpsTelemetry: StateFlow<GpsTelemetry> = _gpsTelemetry.asStateFlow()

    private var locationListening = false

    private val locationListener = LocationListener { location ->
        publishLocation(location)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val granted = hasLocationPermission()
        _gpsTelemetry.value = _gpsTelemetry.value.copy(hasPermission = granted)
        if (!granted || locationListening) return

        val manager = locationManager ?: return
        runCatching {
            val lastGps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            (lastGps ?: lastNet)?.let(::publishLocation)

            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1_000L,
                    1f,
                    locationListener,
                    Looper.getMainLooper()
                )
                locationListening = true
            } else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2_000L,
                    5f,
                    locationListener,
                    Looper.getMainLooper()
                )
                locationListening = true
            }
        }
    }

    fun stopLocationUpdates() {
        if (!locationListening) return
        runCatching { locationManager?.removeUpdates(locationListener) }
        locationListening = false
    }

    private fun publishLocation(location: Location) {
        val kmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt().coerceAtLeast(0) else 0
        _gpsTelemetry.value = GpsTelemetry(
            hasPermission = true,
            speedKmh = kmh,
            bearingDegrees = if (location.hasBearing()) location.bearing else 0f,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            providerName = location.provider?.uppercase() ?: "GPS"
        )
    }

    fun getBatteryLevelPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else 100
    }

    suspend fun loadInstalledApps(): List<InstalledCarApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val selfPkg = context.packageName

        resolveInfos
            .asSequence()
            .filter { it.activityInfo.packageName != selfPkg }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val icon = runCatching { info.loadIcon(pm) }.getOrNull()
                InstalledCarApp(
                    packageName = pkg,
                    label = label,
                    category = categorizePackage(pkg, label),
                    icon = icon
                )
            }
            .sortedWith(compareBy<InstalledCarApp> { it.category.ordinal }.thenBy { it.label })
            .toList()
    }

    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openDialer(phoneNumber: String = ""): Boolean {
        val uri = if (phoneNumber.isBlank()) Uri.parse("tel:") else Uri.parse("tel:$phoneNumber")
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openMessagesApp(): Boolean {
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
        if (!defaultSms.isNullOrBlank() && launchApp(defaultSms)) {
            return true
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MESSAGING)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openBestNavigationApp(installedApps: List<InstalledCarApp>): Boolean {
        val preferred = listOf(
            "org.rajman.neshan.traffic.tehran.navigator", // Neshan
            "ir.balad", // Balad
            "com.waze", // Waze
            "com.google.android.apps.maps" // Google Maps
        )
        for (pkg in preferred) {
            if (installedApps.any { it.packageName == pkg } && launchApp(pkg)) {
                return true
            }
        }
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(geoIntent)
            true
        }.getOrDefault(false)
    }

    private fun categorizePackage(pkg: String, label: String): CarAppCategory {
        val p = "$pkg $label".lowercase()
        return when {
            listOf("maps", "waze", "neshan", "balad", "nav", "gps", "osm").any { p.contains(it) } ->
                CarAppCategory.NAVIGATION
            listOf("music", "spotify", "soundcloud", "radio", "cast", "pod", "audio", "vlc", "player").any { p.contains(it) } ->
                CarAppCategory.MEDIA
            listOf("dialer", "phone", "contacts", "messaging", "sms", "telegram", "whatsapp", "bale", "eitaa").any { p.contains(it) } ->
                CarAppCategory.COMMUNICATION
            else -> CarAppCategory.OTHER
        }
    }
}
