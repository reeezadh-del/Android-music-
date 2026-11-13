package com.example.ui.carplay

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.headunit.AapConnectionStatus
import com.example.headunit.CarAppCategory
import com.example.headunit.GpsTelemetry
import com.example.headunit.HeadunitSettings
import com.example.headunit.HurConnectionMode
import com.example.headunit.InstalledCarApp
import com.example.headunit.UsbHostUiState
import com.example.ui.theme.OutfitFontFamily
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarPlayAndroidAutoProjectionScreen(
    palette: CarPlayPalette,
    settings: HeadunitSettings,
    aapStatus: AapConnectionStatus,
    localIp: String,
    usbState: UsbHostUiState,
    onAttachSurface: (Surface?) -> Unit,
    onStartReceiver: (HurConnectionMode?) -> Unit,
    onStopReceiver: () -> Unit,
    onRefreshUsb: () -> Unit,
    onSendTouch: (Int, Float, Float) -> Unit,
    onSendSteeringKey: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val isActive = aapStatus !is AapConnectionStatus.Idle && aapStatus !is AapConnectionStatus.Error
    val isStreaming = aapStatus is AapConnectionStatus.Streaming

    DisposableEffect(Unit) {
        onDispose {
            onAttachSurface(null)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // CarPlay Top Control HUD Bar
        CarPlayGlassCard(
            palette = palette,
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = if (isStreaming) palette.iosGreen else palette.iosBlue
                    )
                    Column {
                        Text(
                            text = "Android Auto • CarPlay Shell",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "${settings.resolution.label} • ${settings.targetFps} FPS • ${settings.dpi} DPI",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = OutfitFontFamily,
                            color = palette.textSecondary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("projection_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "تنظیمات HUR",
                            tint = palette.textPrimary
                        )
                    }

                    Button(
                        onClick = {
                            if (isActive) onStopReceiver() else onStartReceiver(null)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) palette.iosRed else palette.iosGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("projection_start_stop_btn")
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isActive) "توقف" else "اتصال")
                    }
                }
            }
        }

        // Main H.264 Projection Surface + Overlay Connection Console
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF04070D))
                .border(1.5.dp, palette.cardBorder, RoundedCornerShape(24.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                onAttachSurface(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                onAttachSurface(holder.surface)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                onAttachSurface(null)
                            }
                        })
                        setOnTouchListener { view, event ->
                            val w = view.width.coerceAtLeast(1).toFloat()
                            val h = view.height.coerceAtLeast(1).toFloat()
                            val action = when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> 0
                                MotionEvent.ACTION_UP -> 1
                                MotionEvent.ACTION_MOVE -> 2
                                else -> -1
                            }
                            if (action >= 0) {
                                onSendTouch(action, event.x / w, event.y / h)
                            }
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // When not actively streaming H.264 frames, show clean CarPlay Connection Console
            if (!isStreaming) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.cardSurface.copy(alpha = 0.86f))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Status Banner
                        val (title, detail, accent) = when (aapStatus) {
                            is AapConnectionStatus.Listening -> Triple(
                                "در انتظار اتصال گوشی به پورت ${aapStatus.port}",
                                "در گوشی خود Android Auto ← Developer Settings ← Start head unit server را بزنید یا از Wi-Fi Launcher به آدرس ${aapStatus.localIp}:${aapStatus.port} متصل شوید.",
                                palette.iosOrange
                            )
                            is AapConnectionStatus.Connecting -> Triple(
                                "در حال برقراری ارتباط با ${aapStatus.target}",
                                "پروتکل AAP در حال انتظار برای پاسخ سوکت و تبادل تصویر H.264 است…",
                                palette.iosBlue
                            )
                            is AapConnectionStatus.Handshake -> Triple(
                                "تبادل کلید و پیکربندی نمایشگر",
                                aapStatus.stage,
                                palette.iosTeal
                            )
                            is AapConnectionStatus.Error -> Triple(
                                aapStatus.title,
                                aapStatus.detail,
                                palette.iosRed
                            )
                            is AapConnectionStatus.Streaming -> Triple("", "", palette.iosGreen)
                            AapConnectionStatus.Idle -> Triple(
                                "گیرنده Headunit Reloaded آماده است",
                                "حالت اتصال (Self Mode، سرور وای‌فای یا کابل USB AOAP) را انتخاب کنید تا تصویر اندروید اتو در قاب Apple CarPlay نمایش داده شود.",
                                palette.iosBlue
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(accent.copy(alpha = 0.14f))
                                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary
                                )
                            }
                        }

                        // Connection Mode Quick Cards
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HurConnectionMode.entries.forEach { mode ->
                                val selected = settings.connectionMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { onStartReceiver(mode) },
                                    label = { Text(mode.titleFa) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                HurConnectionMode.USB_AOAP -> Icons.Rounded.Usb
                                                HurConnectionMode.WIFI_SERVER,
                                                HurConnectionMode.WIFI_CLIENT -> Icons.Rounded.Wifi
                                                HurConnectionMode.SELF_MODE -> Icons.Rounded.DirectionsCar
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("projection_mode_${mode.name}")
                                )
                            }
                        }

                        // Network & USB Live Diagnostics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.cardSurfaceElevated)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "آدرس وای‌فای هد‌یونیت",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textSecondary
                                )
                                Text(
                                    text = "$localIp:${settings.port}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.iosBlue
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.cardSurfaceElevated)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "وضعیت USB AOAP",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.textSecondary
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "اسکن مجدد USB",
                                        tint = palette.iosBlue,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable(onClick = onRefreshUsb)
                                    )
                                }
                                Text(
                                    text = usbState.statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Virtual Steering Wheel & CarPlay Input Controls Bar
        CarPlayGlassCard(
            palette = palette,
            cornerRadius = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SteeringWheelButton(
                    icon = Icons.Rounded.SkipPrevious,
                    label = "قبلی",
                    palette = palette,
                    onClick = { onSendSteeringKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                )
                SteeringWheelButton(
                    icon = Icons.Rounded.PlayArrow,
                    label = "پخش/توقف",
                    palette = palette,
                    onClick = { onSendSteeringKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                )
                SteeringWheelButton(
                    icon = Icons.Rounded.SkipNext,
                    label = "بعدی",
                    palette = palette,
                    onClick = { onSendSteeringKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
                )
                SteeringWheelButton(
                    icon = Icons.Rounded.Mic,
                    label = "دستیار صوتی",
                    palette = palette,
                    onClick = { onSendSteeringKey(KeyEvent.KEYCODE_VOICE_ASSIST) }
                )
                SteeringWheelButton(
                    icon = Icons.Rounded.Home,
                    label = "خانه CarPlay",
                    palette = palette,
                    onClick = onBack
                )
            }
        }
    }
}

@Composable
private fun SteeringWheelButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: CarPlayPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = palette.textPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun CarPlayMapsGpsScreen(
    palette: CarPlayPalette,
    gpsTelemetry: GpsTelemetry,
    installedApps: List<InstalledCarApp>,
    onRequestLocationPermission: () -> Unit,
    onOpenBestNav: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val navApps = remember(installedApps) {
        installedApps.filter { it.category == CarAppCategory.NAVIGATION }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        val isWide = maxWidth >= 620.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedometerCard(
                    palette = palette,
                    gpsTelemetry = gpsTelemetry,
                    onRequestLocationPermission = onRequestLocationPermission,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                NavigationAppsCard(
                    palette = palette,
                    navApps = navApps,
                    onOpenBestNav = onOpenBestNav,
                    onLaunchApp = onLaunchApp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedometerCard(
                    palette = palette,
                    gpsTelemetry = gpsTelemetry,
                    onRequestLocationPermission = onRequestLocationPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                )
                NavigationAppsCard(
                    palette = palette,
                    navApps = navApps,
                    onOpenBestNav = onOpenBestNav,
                    onLaunchApp = onLaunchApp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SpeedometerCard(
    palette: CarPlayPalette,
    gpsTelemetry: GpsTelemetry,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    CarPlayGlassCard(palette = palette, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سرعت‌سنج دیجیتال GPS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Text(
                    text = gpsTelemetry.providerName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = OutfitFontFamily,
                    color = palette.iosGreen
                )
            }

            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                val speedRatio = (gpsTelemetry.speedKmh / 220f).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    drawArc(
                        color = palette.textSecondary.copy(alpha = 0.2f),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = palette.iosBlue,
                        startAngle = 140f,
                        sweepAngle = 260f * speedRatio,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (gpsTelemetry.hasPermission) "${gpsTelemetry.speedKmh}" else "--",
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = OutfitFontFamily,
                        color = palette.iosBlue
                    )
                }
            }

            if (!gpsTelemetry.hasPermission) {
                Button(
                    onClick = onRequestLocationPermission,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.iosBlue),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.GpsFixed, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اجازه دسترسی به موقعیت مکانی")
                }
            } else {
                val info = if (gpsTelemetry.latitude != null && gpsTelemetry.longitude != null) {
                    String.format(
                        "Lat %.4f • Lon %.4f • دقت %.0fm",
                        gpsTelemetry.latitude,
                        gpsTelemetry.longitude,
                        gpsTelemetry.accuracyMeters ?: 0f
                    )
                } else {
                    "در حال دریافت سیگنال ماهواره‌ای GPS…"
                }
                Text(
                    text = info,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = OutfitFontFamily,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NavigationAppsCard(
    palette: CarPlayPalette,
    navApps: List<InstalledCarApp>,
    onOpenBestNav: () -> Unit,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CarPlayGlassCard(palette = palette, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "اپلیکیشن‌های مسیریابی نصب‌شده",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Button(
                    onClick = onOpenBestNav,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.iosGreen),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مسیریاب پیش‌فرض")
                }
            }

            if (navApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "اپلیکیشن‌های نشان، بلد، ویز یا Google Maps پس از نصب روی دستگاه اینجا نمایش داده می‌شوند.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    navApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.cardSurfaceElevated)
                                .clickable { onLaunchApp(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppDrawableIcon(app.icon, app.label, Modifier.size(40.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = OutfitFontFamily,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = "اجرا",
                                tint = palette.iosBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CarPlayPhoneScreen(
    palette: CarPlayPalette,
    onCallNumber: (String) -> Unit,
    onOpenMessages: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var dialedNumber by remember { mutableStateOf("") }
    val keypadRows = remember {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
    }

    CarPlayGlassCard(
        palette = palette,
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تلفن و شماره‌گیر CarPlay",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                OutlinedButton(
                    onClick = onOpenMessages,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("پیام‌ها")
                }
            }

            // Number Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.cardSurfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dialedNumber.ifEmpty { "شماره را وارد کنید…" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = OutfitFontFamily,
                    color = if (dialedNumber.isEmpty()) palette.textSecondary else palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (dialedNumber.isNotEmpty()) {
                    IconButton(
                        onClick = { dialedNumber = dialedNumber.dropLast(1) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "پاک کردن",
                            tint = palette.textPrimary
                        )
                    }
                }
            }

            // Keypad Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                keypadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { digit ->
                            Surface(
                                onClick = { dialedNumber += digit },
                                shape = RoundedCornerShape(16.dp),
                                color = palette.cardSurfaceElevated,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = digit,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onCallNumber(dialedNumber) },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.iosGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("carplay_call_button")
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (dialedNumber.isEmpty()) "باز کردن شماره‌گیر گوشی" else "تماس با $dialedNumber",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarPlayInstalledAppsScreen(
    palette: CarPlayPalette,
    installedApps: List<InstalledCarApp>,
    onLaunchApp: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedCategory by remember { mutableStateOf<CarAppCategory?>(null) }
    val filtered = remember(installedApps, selectedCategory) {
        if (selectedCategory == null) installedApps
        else installedApps.filter { it.category == selectedCategory }
    }

    CarPlayGlassCard(
        palette = palette,
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "اپلیکیشن‌های نصب‌شده روی دستگاه (${filtered.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = palette.textPrimary)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("همه") }
                )
                CarAppCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.titleFa) }
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onLaunchApp(app.packageName) }
                            .padding(8.dp)
                    ) {
                        AppDrawableIcon(app.icon, app.label, Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawableIcon(
    drawable: Drawable?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(drawable) {
        drawable?.let {
            val w = it.intrinsicWidth.coerceIn(64, 192)
            val h = it.intrinsicHeight.coerceIn(64, 192)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bmp)
            it.setBounds(0, 0, w, h)
            it.draw(canvas)
            bmp.asImageBitmap()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(16.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2C3E50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DirectionsCar,
                contentDescription = contentDescription,
                tint = Color.White
            )
        }
    }
}
