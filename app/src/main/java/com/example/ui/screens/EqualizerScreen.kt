package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EqualizerBandUi
import com.example.data.model.EqualizerPreset
import com.example.data.model.EqualizerUiState
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.AutoTurquoise
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.OutfitFontFamily
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    equalizerState: EqualizerUiState,
    onSetEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onSetBandLevel: (Int, Int) -> Unit,
    onSetPreampDb: (Float) -> Unit,
    onSetBassBoostPercent: (Int) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Master Toggle & Reset
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.equalizer_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (equalizerState.isEnabled) AutoTurquoise
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                        )
                        Text(
                            text = stringResource(R.string.equalizer_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("eq_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.equalizer_reset))
                    }
                    Switch(
                        checked = equalizerState.isEnabled,
                        onCheckedChange = onSetEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("eq_master_switch")
                    )
                }
            }
        }

        // Live Interactive Frequency Response Curve Canvas
        item {
            FrequencyResponseCurveCard(
                bands = equalizerState.bands,
                isEnabled = equalizerState.isEnabled,
                selectedPreset = equalizerState.selectedPreset,
                sampleRateHz = equalizerState.sampleRateHz,
                audioSessionId = equalizerState.audioSessionId
            )
        }

        // Preset Chips Carousel
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EqualizerPreset.entries, key = { it.name }) { preset ->
                    val selected = equalizerState.selectedPreset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.titleFa) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("eq_preset_${preset.name}")
                    )
                }
            }
        }

        // 5-Band Vertical Mixer Console
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (equalizerState.isEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        },
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Force LTR for the 5 frequency faders so low (60Hz) -> high (14kHz) aligns with the curve
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            equalizerState.bands.forEach { band ->
                                VerticalBandFader(
                                    band = band,
                                    enabled = equalizerState.isEnabled,
                                    onLevelChange = { newMb -> onSetBandLevel(band.bandIndex, newMb) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Media3 Audio Settings Card (Preamp, Bass Boost, Playback Speed, Skip Silence)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = AutoTurquoise
                        )
                        Text(
                            text = stringResource(R.string.equalizer_media3_settings),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    // Preamp Gain Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_preamp),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${if (equalizerState.preampDb >= 0f) "+" else ""}${"%.1f".format(equalizerState.preampDb)} dB",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = equalizerState.preampDb,
                            onValueChange = onSetPreampDb,
                            valueRange = -6f..6f,
                            enabled = equalizerState.isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("eq_preamp_slider")
                        )
                    }

                    // Bass Boost Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_bass_boost),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${equalizerState.bassBoostPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = OutfitFontFamily,
                                color = AmberSecondary
                            )
                        }
                        Slider(
                            value = equalizerState.bassBoostPercent.toFloat(),
                            onValueChange = { onSetBassBoostPercent(it.roundToInt()) },
                            valueRange = 0f..100f,
                            enabled = equalizerState.isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberSecondary,
                                activeTrackColor = AmberSecondary
                            ),
                            modifier = Modifier.testTag("eq_bass_boost_slider")
                        )
                    }

                    // Media3 Playback Speed Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = AutoTurquoise,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.equalizer_playback_speed),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val speeds = listOf(0.8f, 1.0f, 1.2f, 1.5f)
                            speeds.forEach { spd ->
                                val selected = kotlin.math.abs(equalizerState.playbackSpeed - spd) < 0.05f
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetPlaybackSpeed(spd) },
                                    label = {
                                        Text(
                                            text = "${spd}x",
                                            fontFamily = OutfitFontFamily
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AutoTurquoise.copy(alpha = 0.22f),
                                        selectedLabelColor = AutoTurquoise
                                    ),
                                    modifier = Modifier.testTag("eq_speed_$spd")
                                )
                            }
                        }
                    }

                    // Media3 Skip Silence Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.equalizer_skip_silence),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "پرش خودکار از بخش‌های بی‌صدا در پادکست و موسیقی",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = equalizerState.skipSilenceEnabled,
                            onCheckedChange = onSetSkipSilence,
                            modifier = Modifier.testTag("eq_skip_silence_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyResponseCurveCard(
    bands: List<EqualizerBandUi>,
    isEnabled: Boolean,
    selectedPreset: EqualizerPreset,
    sampleRateHz: Int,
    audioSessionId: Int
) {
    val animatedNorms = bands.map { band ->
        val range = (band.maxLevelMb - band.minLevelMb).coerceAtLeast(1)
        val rawNorm = (band.levelMb - band.minLevelMb).toFloat() / range.toFloat()
        animateFloatAsState(
            targetValue = rawNorm.coerceIn(0f, 1f),
            animationSpec = spring(stiffness = 500f),
            label = "curve_${band.bandIndex}"
        ).value
    }

    val lineAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.35f,
        label = "curveAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF171726),
                        Color(0xFF10101A)
                    )
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = null,
                        tint = if (isEnabled) CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = selectedPreset.titleFa,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${sampleRateHz / 1000f}kHz • AudioSession #$audioSessionId",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = OutfitFontFamily,
                    color = AutoTurquoise.copy(alpha = 0.85f)
                )
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .testTag("eq_frequency_curve_canvas")
                ) {
                    val padX = 18.dp.toPx()
                    val padY = 14.dp.toPx()
                    val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
                    val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

                    // Horizontal dB reference lines (+12dB, 0dB, -12dB)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val y = padY + plotH * frac
                        drawLine(
                            color = if (frac == 0.5f) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                            start = Offset(padX, y),
                            end = Offset(size.width - padX, y),
                            strokeWidth = if (frac == 0.5f) 1.5.dp.toPx() else 1.dp.toPx(),
                            pathEffect = if (frac == 0.5f) null else dashEffect
                        )
                    }

                    if (animatedNorms.isEmpty()) return@Canvas

                    val points = animatedNorms.mapIndexed { index, norm ->
                        val x = if (animatedNorms.size == 1) {
                            padX + plotW / 2f
                        } else {
                            padX + (index.toFloat() / (animatedNorms.size - 1)) * plotW
                        }
                        val y = padY + (1f - norm) * plotH
                        Offset(x, y)
                    }

                    // Vertical band guide lines
                    points.forEach { pt ->
                        drawLine(
                            color = Color.White.copy(alpha = 0.07f),
                            start = Offset(pt.x, padY),
                            end = Offset(pt.x, padY + plotH),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Smooth cubic Bezier spline through the 5 frequency control points
                    val curvePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val midX = (p0.x + p1.x) / 2f
                            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(points.last().x, padY + plotH)
                        lineTo(points.first().x, padY + plotH)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CoralPrimary.copy(alpha = 0.34f * lineAlpha),
                                AutoTurquoise.copy(alpha = 0.14f * lineAlpha),
                                Color.Transparent
                            ),
                            startY = padY,
                            endY = padY + plotH
                        )
                    )

                    drawPath(
                        path = curvePath,
                        brush = Brush.horizontalGradient(
                            listOf(
                                CoralPrimary.copy(alpha = lineAlpha),
                                AmberSecondary.copy(alpha = lineAlpha),
                                AutoTurquoise.copy(alpha = lineAlpha)
                            )
                        ),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    points.forEachIndexed { idx, pt ->
                        val nodeColor = when (idx) {
                            0, 1 -> CoralPrimary
                            2 -> AmberSecondary
                            else -> AutoTurquoise
                        }.copy(alpha = lineAlpha)
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.28f * lineAlpha),
                            radius = 10.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = Color(0xFF0E0E17),
                            radius = 5.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = nodeColor,
                            radius = 3.5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalBandFader(
    band: EqualizerBandUi,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = (band.maxLevelMb - band.minLevelMb).coerceAtLeast(1)
    val fraction = ((band.levelMb - band.minLevelMb).toFloat() / range.toFloat()).coerceIn(0f, 1f)
    val dbValue = band.levelMb / 100f

    val activeColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            band.levelMb > 80 -> CoralPrimary
            band.levelMb < -80 -> AutoTurquoise
            else -> AmberSecondary
        },
        label = "faderColor"
    )

    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    fun updateFromY(yPx: Float) {
        if (!enabled || trackHeightPx <= 1f) return
        val clampedNorm = (1f - (yPx / trackHeightPx)).coerceIn(0f, 1f)
        val rawMb = band.minLevelMb + (clampedNorm * range).roundToInt()
        // Snap lightly to 50mB (0.5dB) increments for crisp studio tuning
        val snappedMb = ((rawMb / 50f).roundToInt() * 50).coerceIn(band.minLevelMb, band.maxLevelMb)
        onLevelChange(snappedMb)
    }

    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // dB Gain Badge
        Text(
            text = "${if (dbValue > 0.05f) "+" else ""}${"%.1f".format(dbValue)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = activeColor,
            textAlign = TextAlign.Center
        )

        // Interactive Vertical Fader Track (>= 48dp touch width)
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(190.dp)
                .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled, band.bandIndex) {
                    detectTapGestures { offset ->
                        updateFromY(offset.y)
                    }
                }
                .pointerInput(enabled, band.bandIndex) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        updateFromY(change.position.y)
                    }
                }
                .testTag("eq_band_slider_${band.bandIndex}"),
            contentAlignment = Alignment.Center
        ) {
            // Track groove
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0A0A12))
                    .border(1.dp, Color(0xFF28283C), RoundedCornerShape(50))
            )

            // Center 0 dB tick mark
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(1.5.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // Active Level Bar from 0 dB center toward current gain
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val targetY = size.height * (1f - fraction)
                val topY = minOf(centerY, targetY)
                val barHeight = kotlin.math.abs(targetY - centerY).coerceAtLeast(2f)
                val barW = 8.dp.toPx()
                val left = (size.width - barW) / 2f

                drawRoundRect(
                    color = activeColor.copy(alpha = 0.85f),
                    topLeft = Offset(left, topY),
                    size = androidx.compose.ui.geometry.Size(barW, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
                )

                // Fader Cap / Thumb
                val thumbW = 28.dp.toPx()
                val thumbH = 16.dp.toPx()
                val thumbY = targetY.coerceIn(thumbH / 2f, size.height - thumbH / 2f) - thumbH / 2f
                val thumbX = (size.width - thumbW) / 2f
                drawRoundRect(
                    color = if (enabled) Color(0xFFF5F4FA) else Color(0xFF5A5870),
                    topLeft = Offset(thumbX, thumbY),
                    size = androidx.compose.ui.geometry.Size(thumbW, thumbH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawLine(
                    color = activeColor,
                    start = Offset(thumbX + 6.dp.toPx(), thumbY + thumbH / 2f),
                    end = Offset(thumbX + thumbW - 6.dp.toPx(), thumbY + thumbH / 2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Frequency Label
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = band.frequencyLabel,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = OutfitFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}
