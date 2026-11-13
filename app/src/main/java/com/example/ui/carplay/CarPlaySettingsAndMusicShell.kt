package com.example.ui.carplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AlbumSummary
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.remote.IranianMusicSiteFilter
import com.example.data.remote.OnlineSourceRegion
import com.example.data.remote.OnlineTrackGenre
import com.example.headunit.CarPlayDockSide
import com.example.headunit.CarPlayThemeMode
import com.example.headunit.HeadunitSettings
import com.example.headunit.HurConnectionMode
import com.example.headunit.HurResolution
import com.example.headunit.UsbHostUiState
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.OnlineMusicScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.viewmodel.LibraryFilter
import com.example.ui.viewmodel.OnlineFeedUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarPlaySettingsScreen(
    palette: CarPlayPalette,
    settings: HeadunitSettings,
    localIp: String,
    usbState: UsbHostUiState,
    onUpdateMode: (HurConnectionMode) -> Unit,
    onUpdateHostIp: (String) -> Unit,
    onUpdatePort: (Int) -> Unit,
    onUpdateResolution: (HurResolution) -> Unit,
    onUpdateDpi: (Int) -> Unit,
    onUpdateFps: (Int) -> Unit,
    onUpdateCarPlayTheme: (CarPlayThemeMode) -> Unit,
    onUpdateDockSide: (CarPlayDockSide) -> Unit,
    onUpdateHardwareDecode: (Boolean) -> Unit,
    onUpdateMicPassthrough: (Boolean) -> Unit,
    onUpdateKeepScreenOn: (Boolean) -> Unit,
    onUpdateImmersiveMode: (Boolean) -> Unit,
    onRefreshUsb: () -> Unit,
    onLaunchProjection: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var hostIpInput by remember(settings.clientHostIp) { mutableStateOf(settings.clientHostIp) }
    var portInput by remember(settings.port) { mutableStateOf(settings.port.toString()) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = palette.iosBlue
                    )
                    Text(
                        text = "تنظیمات Headunit Reloaded و ظاهر CarPlay",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = palette.textPrimary)
                }
            }

            // Section 1: Connectivity (Self Mode / Wi-Fi / USB AOAP)
            SettingsSectionCard(
                title = "اتصال و شبکه (HUR)",
                icon = Icons.Rounded.Wifi,
                palette = palette
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HurConnectionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.connectionMode == mode,
                            onClick = { onUpdateMode(mode) },
                            label = { Text(mode.titleFa) },
                            modifier = Modifier.testTag("settings_mode_${mode.name}")
                        )
                    }
                }
                Text(
                    text = settings.connectionMode.subtitleFa,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hostIpInput,
                        onValueChange = {
                            hostIpInput = it
                            onUpdateHostIp(it)
                        },
                        label = { Text("IP سرور (مثلاً 127.0.0.1)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { text ->
                            portInput = text.filter { it.isDigit() }
                            portInput.toIntOrNull()?.let(onUpdatePort)
                        },
                        label = { Text("پورت AAP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IP محلی این دستگاه: $localIp:${settings.port}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = OutfitFontFamily,
                        color = palette.iosBlue
                    )
                    Button(
                        onClick = onRefreshUsb,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.iosTeal)
                    ) {
                        Icon(Icons.Rounded.Usb, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بررسی USB")
                    }
                }
                Text(
                    text = usbState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }

            // Section 2: CarPlay Appearance & Video Resolution
            SettingsSectionCard(
                title = "تنظیمات تصویر، DPI و ظاهر CarPlay",
                icon = Icons.Rounded.DisplaySettings,
                palette = palette
            ) {
                Text(
                    text = "تم ظاهری Apple CarPlay",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CarPlayThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.carPlayTheme == mode,
                            onClick = { onUpdateCarPlayTheme(mode) },
                            label = { Text(mode.titleFa) },
                            modifier = Modifier.testTag("settings_theme_${mode.name}")
                        )
                    }
                }

                Text(
                    text = "جهت نوار داک CarPlay (در حالت افقی)",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CarPlayDockSide.entries.forEach { side ->
                        FilterChip(
                            selected = settings.dockSide == side,
                            onClick = { onUpdateDockSide(side) },
                            label = { Text(side.titleFa) },
                            modifier = Modifier.testTag("settings_dock_${side.name}")
                        )
                    }
                }

                Text(
                    text = "رزولوشن خروجی اندروید اتو",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HurResolution.entries.forEach { res ->
                        FilterChip(
                            selected = settings.resolution == res,
                            onClick = { onUpdateResolution(res) },
                            label = { Text(res.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تراکم پیکسلی (DPI): ${settings.dpi}",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60).forEach { fps ->
                            FilterChip(
                                selected = settings.targetFps == fps,
                                onClick = { onUpdateFps(fps) },
                                label = { Text("$fps FPS") }
                            )
                        }
                    }
                }
                Slider(
                    value = settings.dpi.toFloat(),
                    onValueChange = { onUpdateDpi(it.roundToInt()) },
                    valueRange = 120f..320f,
                    steps = 9
                )
            }

            // Section 3: Decoder, Mic & Screen
            SettingsSectionCard(
                title = "صدا، کدک سخت‌افزاری و صفحه نمایش",
                icon = Icons.Rounded.Mic,
                palette = palette
            ) {
                SettingToggleRow(
                    title = "رمزگشایی سخت‌افزاری H.264 (MediaCodec)",
                    subtitle = "پخش روان ۶۰ فریم بدون فشار به پردازنده",
                    checked = settings.hardwareVideoDecode,
                    onCheckedChange = onUpdateHardwareDecode,
                    palette = palette
                )
                SettingToggleRow(
                    title = "انتقال میکروفون خودرو به گوشی",
                    subtitle = "نیازمند دسترسی میکروفون برای فرمان صوتی",
                    checked = settings.micPassthrough,
                    onCheckedChange = onUpdateMicPassthrough,
                    palette = palette
                )
                SettingToggleRow(
                    title = "روشن ماندن همیشگی صفحه در خودرو",
                    subtitle = "جلوگیری از خاموش شدن نمایشگر حین رانندگی",
                    checked = settings.keepScreenOn,
                    onCheckedChange = onUpdateKeepScreenOn,
                    palette = palette
                )
                SettingToggleRow(
                    title = "حالت تمام‌صفحه فراگیر (Immersive)",
                    subtitle = "حذف حاشیه‌های اضافی برای نمایشگر خودرو",
                    checked = settings.immersiveMode,
                    onCheckedChange = onUpdateImmersiveMode,
                    palette = palette
                )
            }

            Button(
                onClick = onLaunchProjection,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.iosBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("settings_launch_projection")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("شروع گیرنده اندروید اتو با ظاهر CarPlay", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: CarPlayPalette,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.cardSurfaceElevated)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = palette.iosBlue)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        }
        content()
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: CarPlayPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private enum class CarPlayMusicTab(
    val labelFa: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME("خانه", Icons.Rounded.Home),
    ONLINE("آنلاین", Icons.Rounded.Public),
    LIBRARY("کتابخانه", Icons.Rounded.LibraryMusic),
    PLAYLISTS("پلی‌لیست", Icons.Rounded.QueueMusic)
}

@Composable
fun CarPlayMusicSuiteScreen(
    palette: CarPlayPalette,
    allTracks: List<Track>,
    filteredTracks: List<Track>,
    recentTracks: List<Track>,
    albums: List<AlbumSummary>,
    playlists: List<Playlist>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isScanningDeviceMusic: Boolean,
    searchQuery: String,
    libraryFilter: LibraryFilter,
    onlineFeedState: OnlineFeedUiState,
    onlineRegion: OnlineSourceRegion,
    iranianSiteFilter: IranianMusicSiteFilter,
    onlineGenre: OnlineTrackGenre,
    onlineSearchQuery: String,
    onPlayDrivingMix: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onScanDeviceMusic: () -> Unit,
    onImportAudio: () -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDeleteImported: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (LibraryFilter) -> Unit,
    onSelectRegion: (OnlineSourceRegion) -> Unit,
    onSelectIranianSite: (IranianMusicSiteFilter) -> Unit,
    onSelectGenre: (OnlineTrackGenre) -> Unit,
    onOnlineSearchQueryChange: (String) -> Unit,
    onSubmitOnlineSearch: () -> Unit,
    onRefreshOnline: () -> Unit,
    onSaveOnlineTrack: (Track) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableStateOf(CarPlayMusicTab.HOME) }
    val savedIds = remember(allTracks) { allTracks.map { it.id }.toSet() }

    CarPlayGlassCard(
        palette = palette,
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // CarPlay Apple Music Segmented Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.cardSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CarPlayMusicTab.entries.forEach { tab ->
                        val active = selectedTab == tab
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) palette.iosPink else Color.Transparent
                                )
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.labelFa,
                                tint = if (active) Color.White else palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tab.labelFa,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) Color.White else palette.textSecondary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                if (currentTrackId != null) {
                    IconButton(
                        onClick = onOpenNowPlaying,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "در حال پخش",
                            tint = palette.iosPink
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    CarPlayMusicTab.HOME -> HomeScreen(
                        allTracks = allTracks,
                        recentTracks = recentTracks,
                        albums = albums,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        isScanningDeviceMusic = isScanningDeviceMusic,
                        onPlayDrivingMix = onPlayDrivingMix,
                        onOpenCarMode = onOpenCarMode,
                        onScanDeviceMusic = onScanDeviceMusic,
                        onImportAudio = onImportAudio,
                        onPlayAlbum = onPlayAlbum,
                        onPlayTrack = onPlayTrack,
                        onToggleFavorite = onToggleFavorite,
                        onPlayNext = onPlayNext,
                        onAddToPlaylist = onAddToPlaylist,
                        onDeleteImported = onDeleteImported
                    )
                    CarPlayMusicTab.ONLINE -> OnlineMusicScreen(
                        feedState = onlineFeedState,
                        sourceRegion = onlineRegion,
                        selectedIranianSite = iranianSiteFilter,
                        selectedGenre = onlineGenre,
                        searchQuery = onlineSearchQuery,
                        savedTrackIds = savedIds,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onSelectRegion = onSelectRegion,
                        onSelectIranianSite = onSelectIranianSite,
                        onSelectGenre = onSelectGenre,
                        onSearchQueryChange = onOnlineSearchQueryChange,
                        onSubmitSearch = onSubmitOnlineSearch,
                        onRefresh = onRefreshOnline,
                        onPlayTrack = onPlayTrack,
                        onPlayNext = onPlayNext,
                        onSaveToLibrary = onSaveOnlineTrack
                    )
                    CarPlayMusicTab.LIBRARY -> LibraryScreen(
                        tracks = filteredTracks,
                        searchQuery = searchQuery,
                        activeFilter = libraryFilter,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onSearchQueryChange = onSearchQueryChange,
                        onFilterSelect = onFilterSelect,
                        onScanDeviceMusic = onScanDeviceMusic,
                        onImportAudio = onImportAudio,
                        onOpenPlaylists = { selectedTab = CarPlayMusicTab.PLAYLISTS },
                        onPlayTrack = onPlayTrack,
                        onToggleFavorite = onToggleFavorite,
                        onPlayNext = onPlayNext,
                        onAddToPlaylist = onAddToPlaylist,
                        onDeleteImported = onDeleteImported
                    )
                    CarPlayMusicTab.PLAYLISTS -> PlaylistsScreen(
                        playlists = playlists,
                        allTracks = allTracks,
                        onCreatePlaylist = onCreatePlaylist,
                        onPlayPlaylist = onPlayPlaylist,
                        onPlayTrackInPlaylist = onPlayTrack,
                        onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
                        onDeletePlaylist = onDeletePlaylist,
                        onBack = { selectedTab = CarPlayMusicTab.HOME }
                    )
                }
            }
        }
    }
}
