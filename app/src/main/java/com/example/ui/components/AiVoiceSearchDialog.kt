package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiVoiceSearchResult
import com.example.player.AppTab
import com.example.ui.viewmodel.AiVoiceSearchUiState

@Composable
fun AiVoiceSearchDialog(
    state: AiVoiceSearchUiState,
    targetTab: AppTab,
    onStopAndAnalyze: () -> Unit,
    onCancelRecording: () -> Unit,
    onRetryRecord: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onUseSystemSpeech: () -> Unit,
    onApplyToLibrary: (AiVoiceSearchResult) -> Unit,
    onApplyToTrending: (AiVoiceSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is AiVoiceSearchUiState.Idle) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.ai_voice_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (state) {
                    AiVoiceSearchUiState.Idle -> Unit

                    AiVoiceSearchUiState.PermissionRequired -> {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MicOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.ai_voice_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRequestMicPermission,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_voice_grant_permission_button")
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_voice_grant_permission))
                        }
                        OutlinedButton(
                            onClick = onUseSystemSpeech,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_voice_system_fallback_button")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_voice_system_fallback))
                        }
                    }

                    is AiVoiceSearchUiState.Recording -> {
                        val pulseTransition = rememberInfiniteTransition(label = "ai_mic_pulse")
                        val basePulse by pulseTransition.animateFloat(
                            initialValue = 0.94f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_ring"
                        )
                        val liveBoost by animateFloatAsState(
                            targetValue = 1f + state.amplitude * 0.25f,
                            animationSpec = tween(durationMillis = 120),
                            label = "live_amp"
                        )
                        Box(
                            modifier = Modifier.size(104.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .scale(basePulse * liveBoost)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.voice_search_cd),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "00:${state.elapsedSeconds.toString().padStart(2, '0')} / 00:15",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.ai_voice_listening),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.ai_voice_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = onStopAndAnalyze,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_voice_stop_button")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_voice_stop_and_search))
                        }
                    }

                    AiVoiceSearchUiState.Analyzing -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("ai_voice_analyzing_indicator")
                        )
                        Text(
                            text = stringResource(R.string.ai_voice_processing),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    is AiVoiceSearchUiState.Success -> {
                        val res = state.result
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (res.heardText.isNotBlank()) {
                                    Text(
                                        text = "شنیده شد: «${res.heardText}»",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (res.searchQuery.isNotBlank()) {
                                    Text(
                                        text = "عبارت جستجو: ${res.searchQuery}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("ai_voice_query_result")
                                    )
                                }
                                if (res.matchedTrackIds.isNotEmpty()) {
                                    Text(
                                        text = "${res.matchedTrackIds.size} آهنگ مشابه در کتابخانه پیدا شد",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                if (res.aiNote.isNotBlank()) {
                                    Text(
                                        text = res.aiNote,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        if (targetTab != AppTab.TRENDING) {
                            Button(
                                onClick = { onApplyToLibrary(res) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("ai_voice_apply_library_button")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_voice_apply_library))
                            }
                            OutlinedButton(
                                onClick = { onApplyToTrending(res) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("ai_voice_apply_trending_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_voice_apply_trending))
                            }
                        } else {
                            Button(
                                onClick = { onApplyToTrending(res) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("ai_voice_apply_trending_button")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_voice_apply_trending))
                            }
                            OutlinedButton(
                                onClick = { onApplyToLibrary(res) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("ai_voice_apply_library_button")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_voice_apply_library))
                            }
                        }
                    }

                    is AiVoiceSearchUiState.Error -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .testTag("ai_voice_error_text")
                            )
                        }
                        Button(
                            onClick = onRetryRecord,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_voice_retry_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_voice_retry))
                        }
                        OutlinedButton(
                            onClick = onUseSystemSpeech,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_voice_system_fallback_button")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_voice_system_fallback))
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (state is AiVoiceSearchUiState.Recording) {
                TextButton(
                    onClick = onCancelRecording,
                    modifier = Modifier.testTag("ai_voice_cancel_button")
                ) {
                    Text(stringResource(R.string.ai_voice_cancel))
                }
            }
        },
        confirmButton = {
            if (state !is AiVoiceSearchUiState.Recording) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("ai_voice_dismiss_button")
                ) {
                    Text(stringResource(R.string.ai_voice_close))
                }
            }
        }
    )
}
