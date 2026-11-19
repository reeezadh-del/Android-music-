package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.components.FeaturedOnlineSpotlightCard
import com.example.ui.components.OnlineSegmentedSwitcher
import com.example.ui.components.OnlineStudioHeader
import com.example.ui.components.RedesignedOnlineTrackCard

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
    // Lyrics & Shared playback, voice, favorite & social callbacks
    onlyWithLyrics: Boolean,
    onToggleOnlyWithLyrics: () -> Unit,
    onOpenLyrics: (MusicTrack) -> Unit,
    currentTrackId: String?,
    isPlaying: Boolean,
    socialSummaries: Map<String, TrackReactionSummary>,
    onVoiceSearchClick: () -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onToggleReaction: (MusicTrack, TrackReactionType) -> Unit,
    onOpenComments: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isTrending = subSection == OnlineSubSection.TRENDING
    val rawTracks = if (isTrending) trendingTracks else discoverTracks
    val lyricsAvailableCount = rawTracks.count { it.lyrics.isNotBlank() }
    val displayedTracks = if (onlyWithLyrics) {
        rawTracks.filter { it.lyrics.isNotBlank() }
    } else {
        rawTracks
    }
    val isCurrentLoading = if (isTrending) isLoadingTrending else isLoadingDiscover
    val currentError = if (isTrending) trendingError else discoverError
    val onRefresh = if (isTrending) onRefreshTrending else onRefreshDiscover
    val onPlayTrack = if (isTrending) onPlayTrendingTrack else onPlayDiscoverTrack

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("online_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnlineStudioHeader(
                subSection = subSection,
                tracksCount = rawTracks.size,
                lyricsAvailableCount = lyricsAvailableCount,
                isLoading = isCurrentLoading,
                onRefresh = onRefresh,
                modifier = Modifier.padding(top = 8.dp)
            )

            OnlineSegmentedSwitcher(
                subSection = subSection,
                onSelectSubSection = { target ->
                    focusManager.clearFocus()
                    onSelectSubSection(target)
                }
            )

            if (isTrending) {
                AvaSearchField(
                    value = trendingSearchQuery,
                    onValueChange = onTrendingSearchQueryChange,
                    placeholderText = stringResource(R.string.trending_search_hint),
                    voiceContentDescription = stringResource(R.string.voice_search_cd),
                    onVoiceSearchClick = {
                        focusManager.clearFocus()
                        onVoiceSearchClick()
                    },
                    onSubmitSearch = {
                        focusManager.clearFocus()
                        onSubmitTrendingSearch()
                    },
                    onClearSearch = {
                        focusManager.clearFocus()
                        onClearTrendingSearch()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSubmitTrendingSearch()
                        }
                    ),
                    inputTestTag = "trending_search_input",
                    voiceTestTag = "voice_search_trending_button"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "lyrics_filter_chip") {
                        LyricsOnlyFilterChip(
                            selected = onlyWithLyrics,
                            lyricsCount = lyricsAvailableCount,
                            onClick = onToggleOnlyWithLyrics
                        )
                    }
                    items(TrendingCategory.entries, key = { it.name }) { category ->
                        val selected = selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTrendingCategory(category) },
                            label = { Text(category.labelFa) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (category) {
                                        TrendingCategory.ALL -> Icons.Rounded.Whatshot
                                        TrendingCategory.SONGS -> Icons.Rounded.MusicNote
                                        TrendingCategory.REMIXES -> Icons.Rounded.GraphicEq
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("trending_cat_${category.name}")
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(TrendingPreset.entries, key = { it.name }) { preset ->
                        val selected = selectedPreset == preset && trendingSearchQuery.isBlank()
                        FilterChip(
                            selected = selected,
                            onClick = {
                                focusManager.clearFocus()
                                onSelectTrendingPreset(preset)
                            },
                            label = { Text(preset.labelFa) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("trending_preset_${preset.name}")
                        )
                    }
                }
            } else {
                AvaSearchField(
                    value = discoverSearchQuery,
                    onValueChange = onDiscoverSearchQueryChange,
                    placeholderText = stringResource(R.string.online_discover_search_hint),
                    voiceContentDescription = stringResource(R.string.voice_search_cd),
                    onVoiceSearchClick = {
                        focusManager.clearFocus()
                        onVoiceSearchClick()
                    },
                    onSubmitSearch = {
                        focusManager.clearFocus()
                        onSubmitDiscoverSearch()
                    },
                    onClearSearch = {
                        focusManager.clearFocus()
                        onClearDiscoverSearch()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSubmitDiscoverSearch()
                        }
                    ),
                    inputTestTag = "discover_search_input",
                    voiceTestTag = "voice_search_discover_button"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "lyrics_filter_chip_discover") {
                        LyricsOnlyFilterChip(
                            selected = onlyWithLyrics,
                            lyricsCount = lyricsAvailableCount,
                            onClick = onToggleOnlyWithLyrics
                        )
                    }
                    items(OnlineDiscoverStyle.entries, key = { it.name }) { style ->
                        val selected = selectedDiscoverStyle == style && discoverSearchQuery.isBlank()
                        FilterChip(
                            selected = selected,
                            onClick = {
                                focusManager.clearFocus()
                                onSelectDiscoverStyle(style)
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
            }

            // Main Feed Content Area
            when {
                isCurrentLoading && rawTracks.isEmpty() -> {
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
                            CircularProgressIndicator(
                                color = if (isTrending) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            )
                            Text(
                                text = stringResource(
                                    if (isTrending) R.string.trending_loading else R.string.online_discover_loading
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                currentError != null && rawTracks.isEmpty() -> {
                    OnlineEmptyOrErrorState(
                        isTrending = isTrending,
                        title = stringResource(
                            if (isTrending) R.string.trending_empty_title else R.string.online_discover_empty_title
                        ),
                        subtitle = currentError,
                        actionLabel = stringResource(R.string.trending_retry),
                        actionTestTag = if (isTrending) "trending_retry_button" else "online_discover_retry_button",
                        onAction = onRefresh,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                displayedTracks.isEmpty() && onlyWithLyrics && rawTracks.isNotEmpty() -> {
                    OnlineEmptyOrErrorState(
                        isTrending = isTrending,
                        title = "آهنگی با متن آماده در این لیست نبود",
                        subtitle = "می‌توانید فیلتر «فقط دارای متن» را بردارید تا همه ${rawTracks.size} آهنگ آنلاین نمایش داده شوند.",
                        actionLabel = "نمایش همه آهنگ‌ها",
                        actionTestTag = "online_clear_lyrics_filter_button",
                        onAction = onToggleOnlyWithLyrics,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                displayedTracks.isEmpty() -> {
                    OnlineEmptyOrErrorState(
                        isTrending = isTrending,
                        title = stringResource(
                            if (isTrending) R.string.trending_empty_title else R.string.online_discover_empty_title
                        ),
                        subtitle = stringResource(
                            if (isTrending) R.string.trending_empty_subtitle else R.string.online_discover_empty_subtitle
                        ),
                        actionLabel = null,
                        actionTestTag = "",
                        onAction = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                else -> {
                    val featured = displayedTracks.first()
                    val remaining = displayedTracks.drop(1)

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "featured_${featured.id}") {
                            FeaturedOnlineSpotlightCard(
                                track = featured,
                                isTrending = isTrending,
                                isCurrent = currentTrackId == featured.id,
                                isPlaying = isPlaying,
                                socialSummary = socialSummaries[featured.id] ?: TrackReactionSummary(),
                                onPlayClick = { onPlayTrack(featured) },
                                onToggleFavorite = { onToggleFavorite(featured) },
                                onOpenLyrics = { onOpenLyrics(featured) },
                                onToggleReaction = { reaction -> onToggleReaction(featured, reaction) },
                                onOpenComments = { onOpenComments(featured) }
                            )
                        }

                        itemsIndexed(
                            items = remaining,
                            key = { _, item -> item.id }
                        ) { idx, track ->
                            RedesignedOnlineTrackCard(
                                rank = if (isTrending) idx + 2 else null,
                                track = track,
                                isTrending = isTrending,
                                isCurrent = currentTrackId == track.id,
                                isPlaying = isPlaying,
                                socialSummary = socialSummaries[track.id] ?: TrackReactionSummary(),
                                onPlayClick = { onPlayTrack(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onOpenLyrics = { onOpenLyrics(track) },
                                onToggleReaction = { reaction -> onToggleReaction(track, reaction) },
                                onOpenComments = { onOpenComments(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsOnlyFilterChip(
    selected: Boolean,
    lyricsCount: Int,
    onClick: () -> Unit
) {
    val label = if (lyricsCount > 0) {
        "${stringResource(R.string.online_lyrics_only_filter)} ($lyricsCount)"
    } else {
        stringResource(R.string.online_lyrics_only_filter)
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
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
        modifier = Modifier.testTag("online_lyrics_only_filter")
    )
}

@Composable
private fun OnlineEmptyOrErrorState(
    isTrending: Boolean,
    title: String,
    subtitle: String,
    actionLabel: String?,
    actionTestTag: String,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isTrending) Icons.Rounded.Whatshot else Icons.Rounded.Public,
                    contentDescription = null,
                    tint = if (isTrending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (actionTestTag == "online_clear_lyrics_filter_button") {
                        OutlinedButton(
                            onClick = onAction,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag(actionTestTag)
                        ) {
                            Text(actionLabel)
                        }
                    } else {
                        Button(
                            onClick = onAction,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag(actionTestTag)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(actionLabel)
                        }
                    }
                }
            }
        }
    }
}
