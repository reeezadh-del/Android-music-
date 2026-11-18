package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.player.MusicTrack
import com.example.player.OnlineDiscoverStyle
import com.example.player.OnlineSubSection
import com.example.player.TrackReactionSummary
import com.example.player.TrackReactionType
import com.example.player.TrendingCategory
import com.example.player.TrendingPreset
import com.example.ui.components.AvaSearchField
import com.example.ui.components.OnlineTrackRowItem
import com.example.ui.components.StudioHeroBanner

@Composable
fun OnlineMusicScreen(
    subSection: OnlineSubSection,
    onSelectSubSection: (OnlineSubSection) -> Unit,
    // Trending state & callbacks
    trendingTracks: List<MusicTrack>,
    selectedCategory: TrendingCategory,
    selectedPreset: TrendingPreset,
    trendingSearchQuery: String,
    isLoadingTrending: Boolean,
    trendingError: String?,
    onSelectTrendingCategory: (TrendingCategory) -> Unit,
    onSelectTrendingPreset: (TrendingPreset) -> Unit,
    onTrendingSearchQueryChange: (String) -> Unit,
    onSubmitTrendingSearch: () -> Unit,
    onClearTrendingSearch: () -> Unit,
    onRefreshTrending: () -> Unit,
    onPlayTrendingTrack: (MusicTrack) -> Unit,
    // Discover online state & callbacks
    discoverTracks: List<MusicTrack>,
    selectedDiscoverStyle: OnlineDiscoverStyle,
    discoverSearchQuery: String,
    isLoadingDiscover: Boolean,
    discoverError: String?,
    onSelectDiscoverStyle: (OnlineDiscoverStyle) -> Unit,
    onDiscoverSearchQueryChange: (String) -> Unit,
    onSubmitDiscoverSearch: () -> Unit,
    onClearDiscoverSearch: () -> Unit,
    onRefreshDiscover: () -> Unit,
    onPlayDiscoverTrack: (MusicTrack) -> Unit,
    // Shared playback, voice, favorite & social callbacks
    currentTrackId: String?,
    isPlaying: Boolean,
    socialSummaries: Map<String, TrackReactionSummary>,
    onVoiceSearchClick: () -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onToggleReaction: (MusicTrack, TrackReactionType) -> Unit,
    onOpenComments: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrentLoading = if (subSection == OnlineSubSection.TRENDING) isLoadingTrending else isLoadingDiscover
    val activeTracksCount = if (subSection == OnlineSubSection.TRENDING) trendingTracks.size else discoverTracks.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("online_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioHeroBanner(
                modifier = Modifier.padding(top = 8.dp),
                accentColor = if (subSection == OnlineSubSection.TRENDING) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.online_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (activeTracksCount > 0) {
                                    "$activeTracksCount آهنگ آنلاین • ری‌اکت و نظر شنوندگان"
                                } else {
                                    stringResource(R.string.online_subtitle)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                CircleShape
                            )
                    ) {
                        IconButton(
                            onClick = {
                                if (subSection == OnlineSubSection.TRENDING) {
                                    onRefreshTrending()
                                } else {
                                    onRefreshDiscover()
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("online_refresh_button")
                        ) {
                            if (isCurrentLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.trending_refresh),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Segmented Sub-Tab Switcher (Trending Instagram <-> Online Discover)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        RoundedCornerShape(18.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OnlineSubSection.entries.forEach { tab ->
                        val selected = subSection == tab
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectSubSection(tab) }
                                .testTag("online_subtab_${tab.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        OnlineSubSection.TRENDING -> Icons.Rounded.Whatshot
                                        OnlineSubSection.DISCOVER -> Icons.Rounded.Explore
                                    },
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.labelFa,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        when (subSection) {
            OnlineSubSection.TRENDING -> {
                TrendingIranianScreen(
                    tracks = trendingTracks,
                    selectedCategory = selectedCategory,
                    selectedPreset = selectedPreset,
                    searchQuery = trendingSearchQuery,
                    isLoading = isLoadingTrending,
                    errorMessage = trendingError,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onSelectCategory = onSelectTrendingCategory,
                    onSelectPreset = onSelectTrendingPreset,
                    onSearchQueryChange = onTrendingSearchQueryChange,
                    onSubmitSearch = onSubmitTrendingSearch,
                    onClearSearch = onClearTrendingSearch,
                    onVoiceSearchClick = onVoiceSearchClick,
                    onRefresh = onRefreshTrending,
                    onPlayTrack = onPlayTrendingTrack,
                    onToggleFavorite = onToggleFavorite,
                    socialSummaries = socialSummaries,
                    onToggleReaction = onToggleReaction,
                    onOpenComments = onOpenComments,
                    showHeaderBanner = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            OnlineSubSection.DISCOVER -> {
                OnlineDiscoverContent(
                    tracks = discoverTracks,
                    selectedStyle = selectedDiscoverStyle,
                    searchQuery = discoverSearchQuery,
                    isLoading = isLoadingDiscover,
                    errorMessage = discoverError,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    socialSummaries = socialSummaries,
                    onSelectStyle = onSelectDiscoverStyle,
                    onSearchQueryChange = onDiscoverSearchQueryChange,
                    onSubmitSearch = onSubmitDiscoverSearch,
                    onClearSearch = onClearDiscoverSearch,
                    onVoiceSearchClick = onVoiceSearchClick,
                    onRefresh = onRefreshDiscover,
                    onPlayTrack = onPlayDiscoverTrack,
                    onToggleFavorite = onToggleFavorite,
                    onToggleReaction = onToggleReaction,
                    onOpenComments = onOpenComments,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OnlineDiscoverContent(
    tracks: List<MusicTrack>,
    selectedStyle: OnlineDiscoverStyle,
    searchQuery: String,
    isLoading: Boolean,
    errorMessage: String?,
    currentTrackId: String?,
    isPlaying: Boolean,
    socialSummaries: Map<String, TrackReactionSummary>,
    onSelectStyle: (OnlineDiscoverStyle) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onVoiceSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onToggleReaction: (MusicTrack, TrackReactionType) -> Unit,
    onOpenComments: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AvaSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholderText = stringResource(R.string.online_discover_search_hint),
            voiceContentDescription = stringResource(R.string.voice_search_cd),
            onVoiceSearchClick = {
                focusManager.clearFocus()
                onVoiceSearchClick()
            },
            onSubmitSearch = {
                focusManager.clearFocus()
                onSubmitSearch()
            },
            onClearSearch = {
                focusManager.clearFocus()
                onClearSearch()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onSubmitSearch()
                }
            ),
            inputTestTag = "discover_search_input",
            voiceTestTag = "voice_search_discover_button"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(OnlineDiscoverStyle.entries) { style ->
                val selected = selectedStyle == style && searchQuery.isBlank()
                FilterChip(
                    selected = selected,
                    onClick = {
                        focusManager.clearFocus()
                        onSelectStyle(style)
                    },
                    label = { Text(style.labelFa) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.testTag("online_style_${style.name}")
                )
            }
        }

        when {
            isLoading && tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.online_discover_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            errorMessage != null && tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(R.string.online_discover_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onRefresh,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("online_discover_retry_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.trending_retry))
                            }
                        }
                    }
                }
            }

            tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(R.string.online_discover_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.online_discover_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        OnlineTrackRowItem(
                            track = track,
                            isCurrent = currentTrackId == track.id,
                            isPlaying = isPlaying,
                            socialSummary = socialSummaries[track.id] ?: TrackReactionSummary(),
                            onClick = { onPlayTrack(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onToggleReaction = { reaction -> onToggleReaction(track, reaction) },
                            onOpenComments = { onOpenComments(track) }
                        )
                    }
                }
            }
        }
    }
}
