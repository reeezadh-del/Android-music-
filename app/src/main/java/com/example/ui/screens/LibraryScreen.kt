package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Track
import com.example.ui.components.TrackRowItem
import com.example.ui.viewmodel.LibraryFilter

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    searchQuery: String,
    activeFilter: LibraryFilter,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (LibraryFilter) -> Unit,
    onScanDeviceMusic: () -> Unit,
    onImportAudio: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDeleteImported: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_library),
                style = MaterialTheme.typography.headlineMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onScanDeviceMusic,
                    modifier = Modifier.testTag("library_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.action_scan_device_music),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = onOpenPlaylists,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("library_playlists_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.nav_playlists))
                }
                Button(
                    onClick = onImportAudio,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("library_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryFilter.entries) { filter ->
                val selected = filter == activeFilter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter.labelFa) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("library_filter_${filter.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tracks.isEmpty()) {
            BoxEmptyLibraryState(
                isFavorites = activeFilter == LibraryFilter.FAVORITES && searchQuery.isBlank(),
                isEmptyLibrary = searchQuery.isBlank() && activeFilter != LibraryFilter.FAVORITES,
                onScanDeviceMusic = onScanDeviceMusic
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        onPlayClick = { onPlayTrack(track, tracks) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToPlaylistClick = { onAddToPlaylist(track) },
                        onDeleteImported = { onDeleteImported(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxEmptyLibraryState(
    isFavorites: Boolean,
    isEmptyLibrary: Boolean,
    onScanDeviceMusic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = when {
                isFavorites -> Icons.Rounded.FavoriteBorder
                isEmptyLibrary -> Icons.Rounded.MusicNote
                else -> Icons.Rounded.SearchOff
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                when {
                    isFavorites -> R.string.empty_favorites_title
                    isEmptyLibrary -> R.string.empty_device_music_title
                    else -> R.string.empty_search_title
                }
            ),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                when {
                    isFavorites -> R.string.empty_favorites_subtitle
                    isEmptyLibrary -> R.string.empty_device_music_subtitle
                    else -> R.string.empty_search_subtitle
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (isEmptyLibrary) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onScanDeviceMusic,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("library_empty_scan_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_scan_device_music))
            }
        }
    }
}
