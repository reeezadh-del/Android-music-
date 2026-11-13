package com.example.audio

import com.example.data.model.EqualizerBandUi
import com.example.data.model.EqualizerPreset
import com.example.data.model.EqualizerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EqualizerController {

    private val bandFreqs = intArrayOf(60, 230, 910, 3600, 14000)
    private const val MIN_MB = -1200
    private const val MAX_MB = 1200

    private var currentSessionId: Int = 0
    private var desiredEnabled: Boolean = true
    private var desiredPreset: EqualizerPreset = EqualizerPreset.ROAD_TRIP
    private var currentLevelsMb: IntArray = EqualizerPreset.ROAD_TRIP.bandOffsetsMb.toIntArray()
    private var preampDb: Float = 0f
    private var bassBoostPercent: Int = 0
    private var playbackSpeed: Float = 1.0f
    private var skipSilenceEnabled: Boolean = false
    private var sampleRateHz: Int = 44100

    private val _state = MutableStateFlow(buildUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId > 0) {
            currentSessionId = audioSessionId
        }
        syncProcessor()
        publishState()
    }

    fun restoreSavedConfig(
        enabled: Boolean,
        preset: EqualizerPreset,
        bandLevelsMb: List<Int>,
        savedPreampDb: Float,
        savedBassBoostPercent: Int,
        savedPlaybackSpeed: Float,
        savedSkipSilence: Boolean
    ) {
        desiredEnabled = enabled
        desiredPreset = preset
        val levels = if (preset == EqualizerPreset.CUSTOM && bandLevelsMb.size == 5) {
            bandLevelsMb
        } else if (bandLevelsMb.size == 5 && preset != EqualizerPreset.FLAT) {
            bandLevelsMb
        } else {
            preset.bandOffsetsMb
        }
        for (i in 0 until 5) {
            currentLevelsMb[i] = levels.getOrElse(i) { 0 }.coerceIn(MIN_MB, MAX_MB)
        }
        preampDb = savedPreampDb.coerceIn(-6f, 6f)
        bassBoostPercent = savedBassBoostPercent.coerceIn(0, 100)
        playbackSpeed = savedPlaybackSpeed.coerceIn(0.75f, 1.5f)
        skipSilenceEnabled = savedSkipSilence
        syncProcessor()
        publishState()
    }

    fun setEnabled(enabled: Boolean) {
        desiredEnabled = enabled
        syncProcessor()
        publishState()
    }

    fun applyPreset(preset: EqualizerPreset) {
        desiredPreset = preset
        if (preset != EqualizerPreset.CUSTOM) {
            for (i in 0 until 5) {
                currentLevelsMb[i] = preset.bandOffsetsMb.getOrElse(i) { 0 }.coerceIn(MIN_MB, MAX_MB)
            }
        }
        syncProcessor()
        publishState()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        if (bandIndex !in 0..4) return
        currentLevelsMb[bandIndex] = levelMb.coerceIn(MIN_MB, MAX_MB)
        desiredPreset = EqualizerPreset.CUSTOM
        syncProcessor()
        publishState()
    }

    fun setPreampDb(db: Float) {
        preampDb = db.coerceIn(-6f, 6f)
        syncProcessor()
        publishState()
    }

    fun setBassBoostPercent(percent: Int) {
        bassBoostPercent = percent.coerceIn(0, 100)
        syncProcessor()
        publishState()
    }

    fun updateMedia3Settings(speed: Float, skipSilence: Boolean, sampleRate: Int? = null) {
        playbackSpeed = speed.coerceIn(0.75f, 1.5f)
        skipSilenceEnabled = skipSilence
        if (sampleRate != null && sampleRate > 0) {
            sampleRateHz = sampleRate
        }
        publishState()
    }

    fun resetToFlat() {
        desiredPreset = EqualizerPreset.FLAT
        currentLevelsMb.fill(0)
        preampDb = 0f
        bassBoostPercent = 0
        syncProcessor()
        publishState()
    }

    private fun syncProcessor() {
        Media3EqualizerAudioProcessor.updateParameters(
            isEnabled = desiredEnabled,
            bandLevelsMb = currentLevelsMb.toList(),
            preampDb = preampDb,
            bassBoostPercent = bassBoostPercent
        )
    }

    private fun publishState() {
        _state.value = buildUiState()
    }

    private fun buildUiState(): EqualizerUiState {
        val bands = (0 until 5).map { idx ->
            val hz = bandFreqs[idx]
            EqualizerBandUi(
                bandIndex = idx,
                frequencyHz = hz,
                frequencyLabel = formatFreq(hz),
                levelMb = currentLevelsMb[idx],
                minLevelMb = MIN_MB,
                maxLevelMb = MAX_MB
            )
        }
        return EqualizerUiState(
            isSupported = true,
            isEnabled = desiredEnabled,
            selectedPreset = desiredPreset,
            bands = bands,
            preampDb = preampDb,
            bassBoostPercent = bassBoostPercent,
            playbackSpeed = playbackSpeed,
            skipSilenceEnabled = skipSilenceEnabled,
            audioSessionId = currentSessionId,
            sampleRateHz = sampleRateHz
        )
    }

    private fun formatFreq(hz: Int): String =
        if (hz >= 1000) {
            val k = hz / 1000f
            if (k % 1f == 0f) "${k.toInt()}kHz" else "${"%.1f".format(k)}kHz"
        } else {
            "${hz}Hz"
        }
}
