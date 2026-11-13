package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AlbumSummary
import com.example.data.model.AutoBrowseNodePreview
import com.example.data.model.EqualizerPreset
import com.example.data.model.EqualizerUiState
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.service.AvaMediaLibraryService
import com.example.ui.theme.AutoTurquoise
import com.example.ui.theme.OutfitFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoHubScreen(
    isServiceConnected: Boolean,
    browseNodes: List<AutoBrowseNodePreview>,
    allTracks: List<Track>,
    favoriteTracks: List<Track>,
    albums: List<AlbumSummary>,
    playlists: List<Playlist>,
    equalizerState: EqualizerUiState,
    sleepTimerRemainingMinutes: Int,
    onOpenCarMode: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onSetEqualizerEnabled: (Boolean) -> Unit,
    onSelectEqualizerPreset: (EqualizerPreset) -> Unit,
    onSetEqualizerBand: (Int, Int) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedNodeId by remember { mutableStateOf<String?>(AvaMediaLibraryService.TAB_ALL_SONGS) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.auto_hub_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Android Auto Connection & Service Status Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0C2729),
                                Color(0xFF151724)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        AutoTurquoise.copy(alpha = 0.45f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AutoTurquoise.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsCar,
                                contentDescription = null,
                                tint = AutoTurquoise
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Android Auto Ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = OutfitFontFamily,
                                    color = AutoTurquoise
                                )
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = AutoTurquoise,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isServiceConnected) {
                                    stringResource(R.string.auto_service_active)
                                } else {
                                    "در حال اتصال به سرویس رسانه…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.auto_voice_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = onOpenCarMode,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AutoTurquoise,
                            contentColor = Color(0xFF002522)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auto_hub_launch_car_mode")
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "باز کردن حالت رانندگی تمام‌صفحه",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Interactive Android Auto Browse Tree Preview
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.auto_browse_preview_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.auto_browse_preview_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    browseNodes.forEach { node ->
                        val isExpanded = expandedNodeId == node.id
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isExpanded) AutoTurquoise.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(18.dp)
                                )
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedNodeId = if (isExpanded) null else node.id
                                        }
                                        .padding(14.dp)
                                        .testTag("auto_node_${node.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(AutoTurquoise.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = node.iconRes),
                                            contentDescription = node.title,
                                            tint = AutoTurquoise,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = node.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = node.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        when (node.id) {
                                            AvaMediaLibraryService.TAB_ALL_SONGS -> {
                                                allTracks.forEach { track ->
                                                    AutoPreviewSubRow(
                                                        title = track.title,
                                                        subtitle = track.artist,
                                                        onClick = { onPlayTrack(track, allTracks) }
                                                    )
                                                }
                                            }
                                            AvaMediaLibraryService.TAB_ALBUMS -> {
                                                albums.forEach { album ->
                                                    AutoPreviewSubRow(
                                                        title = album.name,
                                                        subtitle = "${album.artist} • ${album.trackCount} آهنگ",
                                                        onClick = { onPlayAlbum(album) }
                                                    )
                                                }
                                            }
                                            AvaMediaLibraryService.TAB_FAVORITES -> {
                                                if (favoriteTracks.isEmpty()) {
                                                    Text(
                                                        text = "آهنگی در علاقه‌مندی‌ها نیست",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    favoriteTracks.forEach { track ->
                                                        AutoPreviewSubRow(
                                                            title = track.title,
                                                            subtitle = track.artist,
                                                            onClick = { onPlayTrack(track, favoriteTracks) }
                                                        )
                                                    }
                                                }
                                            }
                                            AvaMediaLibraryService.TAB_PLAYLISTS -> {
                                                playlists.forEach { playlist ->
                                                    AutoPreviewSubRow(
                                                        title = playlist.name,
                                                        subtitle = "${playlist.trackIds.size} آهنگ",
                                                        onClick = { onPlayPlaylist(playlist) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real 5-Band Equalizer Section
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                imageVector = Icons.Rounded.Equalizer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.equalizer_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Switch(
                            checked = equalizerState.isEnabled,
                            onCheckedChange = onSetEqualizerEnabled,
                            modifier = Modifier.testTag("equalizer_switch")
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EqualizerPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = equalizerState.selectedPreset == preset,
                                onClick = { onSelectEqualizerPreset(preset) },
                                label = { Text(preset.titleFa) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    equalizerState.bands.forEach { band ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = band.frequencyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(46.dp)
                            )
                            Slider(
                                value = band.levelMb.toFloat(),
                                onValueChange = { onSetEqualizerBand(band.bandIndex, it.toInt()) },
                                valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                                enabled = equalizerState.isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            val db = band.levelMb / 100f
                            Text(
                                text = "${if (db >= 0) "+" else ""}${"%.1f".format(db)}dB",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                }
            }
        }

        // Sleep Timer Section
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.sleep_timer_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        if (sleepTimerRemainingMinutes > 0) {
                            Text(
                                text = "$sleepTimerRemainingMinutes دقیقه مانده",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val options = listOf(0, 15, 30, 45, 60)
                        options.forEach { mins ->
                            val label = if (mins == 0) {
                                stringResource(R.string.sleep_timer_off)
                            } else {
                                "$mins دقیقه"
                            }
                            FilterChip(
                                selected = if (mins == 0) sleepTimerRemainingMinutes == 0 else sleepTimerRemainingMinutes == mins,
                                onClick = { onSetSleepTimer(mins) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("sleep_timer_$mins")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoPreviewSubRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = AutoTurquoise,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.cd_play),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
