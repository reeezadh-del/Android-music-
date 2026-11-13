package com.example.ui.viewmodel

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AvaPlayerApplication
import com.example.headunit.AapConnectionStatus
import com.example.headunit.CarPlayDockSide
import com.example.headunit.CarPlayThemeMode
import com.example.headunit.GpsTelemetry
import com.example.headunit.HeadunitSettings
import com.example.headunit.HurConnectionMode
import com.example.headunit.HurResolution
import com.example.headunit.InstalledCarApp
import com.example.headunit.UsbHostUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CarPlaySurfacePage {
    DASHBOARD,
    APP_GRID,
    AA_PROJECTION,
    MUSIC,
    MAPS_GPS,
    PHONE,
    EQUALIZER,
    CAR_APPS,
    SETTINGS
}

class HeadunitViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AvaPlayerApplication
    private val preferences = app.headunitPreferences
    private val usbManager = app.aoapUsbManager
    private val aapClient = app.aapProtocolClient
    private val deviceData = app.deviceCarDataProvider

    val settings: StateFlow<HeadunitSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeadunitSettings())

    val aapStatus: StateFlow<AapConnectionStatus> = aapClient.status
    val localIpAddress: StateFlow<String> = aapClient.localIpAddress
    val usbState: StateFlow<UsbHostUiState> = usbManager.uiState
    val gpsTelemetry: StateFlow<GpsTelemetry> = deviceData.gpsTelemetry

    private val _currentPage = MutableStateFlow(CarPlaySurfacePage.DASHBOARD)
    val currentPage: StateFlow<CarPlaySurfacePage> = _currentPage.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledCarApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledCarApp>> = _installedApps.asStateFlow()

    private val _clockText = MutableStateFlow(formatCurrentTime())
    val clockText: StateFlow<String> = _clockText.asStateFlow()

    private val _batteryPercent = MutableStateFlow(deviceData.getBatteryLevelPercent())
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private val _headunitMessage = MutableStateFlow<String?>(null)
    val headunitMessage: StateFlow<String?> = _headunitMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _installedApps.value = deviceData.loadInstalledApps()
        }
        viewModelScope.launch {
            while (true) {
                _clockText.value = formatCurrentTime()
                _batteryPercent.value = deviceData.getBatteryLevelPercent()
                delay(15_000L)
            }
        }
        deviceData.startLocationUpdates()
    }

    fun navigateTo(page: CarPlaySurfacePage) {
        _currentPage.value = page
    }

    fun toggleDashboardAndGrid() {
        _currentPage.value = when (_currentPage.value) {
            CarPlaySurfacePage.DASHBOARD -> CarPlaySurfacePage.APP_GRID
            else -> CarPlaySurfacePage.DASHBOARD
        }
    }

    fun attachVideoSurface(surface: Surface?) {
        aapClient.attachSurface(surface)
    }

    fun startReceiver(overrideMode: HurConnectionMode? = null) {
        viewModelScope.launch {
            val activeSettings = if (overrideMode != null) {
                preferences.updateSettings { it.copy(connectionMode = overrideMode) }
                settings.value.copy(connectionMode = overrideMode)
            } else {
                settings.value
            }
            aapClient.start(activeSettings)
        }
    }

    fun stopReceiver() {
        aapClient.stop()
    }

    fun refreshUsbDevices() {
        usbManager.refreshDevices()
        val requested = usbManager.requestPermissionForFirstDevice()
        if (requested) {
            _headunitMessage.value = "لطفاً مجوز دسترسی USB را تایید کنید"
        }
    }

    fun sendProjectionTouch(action: Int, normalizedX: Float, normalizedY: Float) {
        aapClient.sendTouchEvent(action, normalizedX, normalizedY)
    }

    fun sendSteeringKey(keyCode: Int) {
        aapClient.sendSteeringKey(keyCode)
    }

    fun updateConnectionMode(mode: HurConnectionMode) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(connectionMode = mode) }
        }
    }

    fun updateClientHostIp(ip: String) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(clientHostIp = ip) }
        }
    }

    fun updatePort(port: Int) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(port = port.coerceIn(1024, 65535)) }
        }
    }

    fun updateResolution(resolution: HurResolution) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(resolution = resolution) }
        }
    }

    fun updateDpi(dpi: Int) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(dpi = dpi.coerceIn(120, 320)) }
        }
    }

    fun updateTargetFps(fps: Int) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(targetFps = if (fps <= 30) 30 else 60) }
        }
    }

    fun updateCarPlayTheme(theme: CarPlayThemeMode) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(carPlayTheme = theme) }
        }
    }

    fun updateDockSide(side: CarPlayDockSide) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(dockSide = side) }
        }
    }

    fun updateHardwareDecode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(hardwareVideoDecode = enabled) }
        }
    }

    fun updateMicPassthrough(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(micPassthrough = enabled) }
        }
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(keepScreenOn = enabled) }
        }
    }

    fun updateImmersiveMode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateSettings { it.copy(immersiveMode = enabled) }
        }
    }

    fun refreshLocationPermissionState() {
        deviceData.startLocationUpdates()
    }

    fun launchInstalledApp(packageName: String) {
        val ok = deviceData.launchApp(packageName)
        if (!ok) {
            _headunitMessage.value = "اجرای برنامه امکان‌پذیر نبود"
        }
    }

    fun openBestNavigation() {
        if (!deviceData.openBestNavigationApp(_installedApps.value)) {
            _headunitMessage.value = "اپلیکیشن مسیریابی روی دستگاه پیدا نشد"
        }
    }

    fun openDialer(number: String = "") {
        if (!deviceData.openDialer(number)) {
            _headunitMessage.value = "برنامه تلفن در این دستگاه در دسترس نیست"
        }
    }

    fun openMessages() {
        if (!deviceData.openMessagesApp()) {
            _headunitMessage.value = "برنامه پیام‌رسان روی دستگاه پیدا نشد"
        }
    }

    fun clearMessage() {
        _headunitMessage.value = null
    }

    private fun formatCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }
}
