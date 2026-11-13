package com.example.headunit

import android.graphics.drawable.Drawable

enum class HurConnectionMode(val titleFa: String, val subtitleFa: String) {
    SELF_MODE(
        titleFa = "حالت خودکار (Self Mode)",
        subtitleFa = "اتصال مستقیم به سرور Headunit اندروید اتو روی همین دستگاه (127.0.0.1:5277)"
    ),
    WIFI_SERVER(
        titleFa = "سرور وای‌فای (Wi-Fi)",
        subtitleFa = "ایجاد سرور روی پورت 5277 برای اتصال بی‌سیم گوشی دیگر"
    ),
    WIFI_CLIENT(
        titleFa = "اتصال به IP وای‌فای",
        subtitleFa = "اتصال مستقیم به IP گوشی میزبان (Headunit Server)"
    ),
    USB_AOAP(
        titleFa = "کابل USB (AOAP)",
        subtitleFa = "شناسایی گوشی متصل از طریق کابل OTG و پروتکل Android Open Accessory"
    )
}

enum class HurResolution(val width: Int, val height: Int, val label: String) {
    SD_480P(800, 480, "800×480 (WVGA)"),
    HD_720P(1280, 720, "1280×720 (HD)"),
    FHD_1080P(1920, 1080, "1920×1080 (Full HD)")
}

enum class CarPlayThemeMode(val titleFa: String) {
    NIGHT_GLASS("شیشه‌ای شب (Night)"),
    DAY_FROST("یخی روز (Day)"),
    AUTO("خودکار (Auto)")
}

enum class CarPlayDockSide(val titleFa: String) {
    LEFT("چپ (فرمان چپ)"),
    RIGHT("راست (فرمان راست)")
}

data class HeadunitSettings(
    val connectionMode: HurConnectionMode = HurConnectionMode.WIFI_SERVER,
    val clientHostIp: String = "127.0.0.1",
    val port: Int = 5277,
    val resolution: HurResolution = HurResolution.HD_720P,
    val dpi: Int = 160,
    val targetFps: Int = 60,
    val carPlayTheme: CarPlayThemeMode = CarPlayThemeMode.NIGHT_GLASS,
    val dockSide: CarPlayDockSide = CarPlayDockSide.LEFT,
    val hardwareVideoDecode: Boolean = true,
    val micPassthrough: Boolean = false,
    val keepScreenOn: Boolean = true,
    val immersiveMode: Boolean = true
)

sealed interface AapConnectionStatus {
    data object Idle : AapConnectionStatus
    data class Listening(val localIp: String, val port: Int) : AapConnectionStatus
    data class Connecting(val target: String) : AapConnectionStatus
    data class Handshake(val stage: String) : AapConnectionStatus
    data class Streaming(
        val transport: String,
        val protocolVersion: String,
        val width: Int,
        val height: Int,
        val fps: Int,
        val bytesReceived: Long,
        val videoFrames: Long
    ) : AapConnectionStatus

    data class Error(val title: String, val detail: String) : AapConnectionStatus
}

data class UsbDeviceSummary(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val productLabel: String,
    val isAoapMode: Boolean,
    val hasPermission: Boolean
)

data class UsbHostUiState(
    val devices: List<UsbDeviceSummary> = emptyList(),
    val activeAccessoryLabel: String? = null,
    val statusMessage: String = "کابل USB OTG متصل نیست",
    val isUsbConnected: Boolean = false
)

enum class CarAppCategory(val titleFa: String) {
    NAVIGATION("مسیریابی"),
    MEDIA("رسانه"),
    COMMUNICATION("ارتباطات"),
    OTHER("سایر برنامه‌ها")
}

data class InstalledCarApp(
    val packageName: String,
    val label: String,
    val category: CarAppCategory,
    val icon: Drawable?
)

data class GpsTelemetry(
    val hasPermission: Boolean = false,
    val speedKmh: Int = 0,
    val bearingDegrees: Float = 0f,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val providerName: String = "GPS"
)
