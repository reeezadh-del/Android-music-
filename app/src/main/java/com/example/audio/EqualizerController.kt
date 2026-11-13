package com.example.audio

import android.media.audiofx.Equalizer
import com.example.data.model.EqualizerBandUi
import com.example.data.model.EqualizerPreset
import com.example.data.model.EqualizerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EqualizerController {

    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0
    private var desiredEnabled: Boolean = false
    private var desiredPreset: EqualizerPreset = EqualizerPreset.ROAD_TRIP
    private var customLevelsMb: MutableMap<Int, Int> = mutableMapOf()

    private val _state = MutableStateFlow(defaultPlaceholderState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId && equalizer != null) return
        release()
        currentSessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = desiredEnabled
            equalizer = eq
            applyPresetInternal(eq, desiredPreset)
            publishFromHardware(eq)
        } catch (_: Throwable) {
            // Fallback gracefully on devices/emulators without AudioEffect hardware support
            equalizer = null
            publishFallbackState(supported = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        desiredEnabled = enabled
        val eq = equalizer
        if (eq != null) {
            runCatching {
                eq.enabled = enabled
                publishFromHardware(eq)
            }.onFailure {
                publishFallbackState(supported = false)
            }
        } else {
            _state.value = _state.value.copy(isEnabled = enabled)
        }
    }

    fun applyPreset(preset: EqualizerPreset) {
        desiredPreset = preset
        customLevelsMb.clear()
        val eq = equalizer
        if (eq != null) {
            runCatching {
                applyPresetInternal(eq, preset)
                publishFromHardware(eq)
            }
        } else {
            publishFallbackState(supported = _state.value.isSupported)
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        customLevelsMb[bandIndex] = levelMb
        val eq = equalizer
        if (eq != null) {
            runCatching {
                val range = eq.bandLevelRange
                val clamped = levelMb.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                eq.setBandLevel(bandIndex.toShort(), clamped)
                publishFromHardware(eq)
            }
        } else {
            val updatedBands = _state.value.bands.map { band ->
                if (band.bandIndex == bandIndex) band.copy(levelMb = levelMb) else band
            }
            _state.value = _state.value.copy(bands = updatedBands)
        }
    }

    private fun applyPresetInternal(eq: Equalizer, preset: EqualizerPreset) {
        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val min = range[0].toInt()
        val max = range[1].toInt()
        for (i in 0 until bandCount) {
            val target = customLevelsMb[i] ?: preset.bandOffsetsMb.getOrElse(i) { 0 }
            eq.setBandLevel(i.toShort(), target.coerceIn(min, max).toShort())
        }
    }

    private fun publishFromHardware(eq: Equalizer) {
        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val min = range[0].toInt()
        val max = range[1].toInt()
        val bands = (0 until bandCount).map { idx ->
            val freqHz = eq.getCenterFreq(idx.toShort()) / 1000
            EqualizerBandUi(
                bandIndex = idx,
                frequencyLabel = formatFreq(freqHz),
                levelMb = eq.getBandLevel(idx.toShort()).toInt(),
                minLevelMb = min,
                maxLevelMb = max
            )
        }
        _state.value = EqualizerUiState(
            isSupported = true,
            isEnabled = eq.enabled,
            selectedPreset = desiredPreset,
            bands = bands
        )
    }

    private fun publishFallbackState(supported: Boolean) {
        val freqs = listOf(60, 230, 910, 3600, 14000)
        val bands = freqs.mapIndexed { idx, hz ->
            val level = customLevelsMb[idx] ?: desiredPreset.bandOffsetsMb.getOrElse(idx) { 0 }
            EqualizerBandUi(
                bandIndex = idx,
                frequencyLabel = formatFreq(hz),
                levelMb = level,
                minLevelMb = -1500,
                maxLevelMb = 1500
            )
        }
        _state.value = EqualizerUiState(
            isSupported = supported,
            isEnabled = desiredEnabled,
            selectedPreset = desiredPreset,
            bands = bands
        )
    }

    private fun defaultPlaceholderState(): EqualizerUiState {
        val freqs = listOf(60, 230, 910, 3600, 14000)
        return EqualizerUiState(
            isSupported = true,
            isEnabled = false,
            selectedPreset = EqualizerPreset.ROAD_TRIP,
            bands = freqs.mapIndexed { idx, hz ->
                EqualizerBandUi(
                    bandIndex = idx,
                    frequencyLabel = formatFreq(hz),
                    levelMb = EqualizerPreset.ROAD_TRIP.bandOffsetsMb[idx],
                    minLevelMb = -1500,
                    maxLevelMb = 1500
                )
            }
        )
    }

    private fun formatFreq(hz: Int): String =
        if (hz >= 1000) "${hz / 1000}k" else "${hz}Hz"

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        currentSessionId = 0
    }
}
