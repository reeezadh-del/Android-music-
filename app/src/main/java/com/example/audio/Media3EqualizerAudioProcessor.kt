package com.example.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time 5-band Biquad Graphic Equalizer + Low-Shelf Bass Boost + Preamp
 * implemented directly as a Media3 [BaseAudioProcessor] inside ExoPlayer's audio pipeline.
 */
@OptIn(UnstableApi::class)
object Media3EqualizerAudioProcessor : BaseAudioProcessor() {

    val BAND_FREQUENCIES_HZ = intArrayOf(60, 230, 910, 3600, 14000)
    private const val PEAKING_Q = 1.41
    private const val BASS_SHELF_HZ = 105.0
    private const val TOTAL_FILTERS = 6 // 5 peaking bands + 1 low-shelf bass boost

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var bandGainsDb = FloatArray(5) { 0f }

    @Volatile
    private var preampGainLinear: Double = 1.0

    @Volatile
    private var bassBoostDb: Float = 0f

    @Volatile
    private var coefficientsDirty: Boolean = true

    private var currentSampleRate: Int = 44100
    private var currentChannelCount: Int = 2

    private val _sampleRateFlow = MutableStateFlow(44100)
    val sampleRateFlow: StateFlow<Int> = _sampleRateFlow.asStateFlow()

    // Biquad coefficients: [filterIndex][b0, b1, b2, a1, a2]
    private var coeffs = Array(TOTAL_FILTERS) { doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0) }

    // Filter state: [filterIndex][channelIndex] -> (z1, z2)
    private var z1 = Array(TOTAL_FILTERS) { DoubleArray(8) }
    private var z2 = Array(TOTAL_FILTERS) { DoubleArray(8) }

    fun updateParameters(
        isEnabled: Boolean,
        bandLevelsMb: List<Int>,
        preampDb: Float,
        bassBoostPercent: Int
    ) {
        enabled = isEnabled
        val newGains = FloatArray(5) { idx ->
            (bandLevelsMb.getOrElse(idx) { 0 } / 100f).coerceIn(-12f, 12f)
        }
        bandGainsDb = newGains
        preampGainLinear = 10.0.pow((preampDb.coerceIn(-6f, 6f) / 20.0))
        bassBoostDb = (bassBoostPercent.coerceIn(0, 100) / 100f) * 8.0f
        coefficientsDirty = true
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        currentSampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        currentChannelCount = inputAudioFormat.channelCount.coerceIn(1, 8)
        _sampleRateFlow.value = currentSampleRate
        recomputeCoefficients()
        resetDelayLines()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)

        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (coefficientsDirty) {
            recomputeCoefficients()
        }

        val channels = currentChannelCount
        val localCoeffs = coeffs
        val preamp = preampGainLinear
        val frameCount = remaining / (2 * channels)

        for (frame in 0 until frameCount) {
            for (ch in 0 until channels) {
                var sample = inputBuffer.getShort() / 32768.0
                sample *= preamp

                for (f in 0 until TOTAL_FILTERS) {
                    val c = localCoeffs[f]
                    val x = sample
                    val y = c[0] * x + z1[f][ch]
                    z1[f][ch] = c[1] * x - c[3] * y + z2[f][ch]
                    z2[f][ch] = c[2] * x - c[4] * y
                    sample = y
                }

                // Soft-clipping limiter to keep boosted frequencies clean without harsh digital clipping
                val limited = if (sample > 0.9 || sample < -0.9) {
                    tanh(sample)
                } else {
                    sample
                }
                val pcm = (limited * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                outputBuffer.putShort(pcm.toShort())
            }
        }

        // Copy any trailing bytes if input wasn't frame-aligned
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        resetDelayLines()
    }

    override fun onReset() {
        resetDelayLines()
        coefficientsDirty = true
    }

    private fun resetDelayLines() {
        for (f in 0 until TOTAL_FILTERS) {
            z1[f].fill(0.0)
            z2[f].fill(0.0)
        }
    }

    private fun recomputeCoefficients() {
        val sr = currentSampleRate.toDouble()
        val gains = bandGainsDb
        val nextCoeffs = Array(TOTAL_FILTERS) { doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0) }

        for (i in 0 until 5) {
            val freq = BAND_FREQUENCIES_HZ[i].toDouble().coerceAtMost(sr * 0.45)
            nextCoeffs[i] = computePeakingEq(freq, sr, PEAKING_Q, gains[i].toDouble())
        }
        nextCoeffs[5] = computeLowShelfEq(BASS_SHELF_HZ, sr, bassBoostDb.toDouble())

        coeffs = nextCoeffs
        coefficientsDirty = false
    }

    private fun computePeakingEq(
        freqHz: Double,
        sampleRate: Double,
        q: Double,
        gainDb: Double
    ): DoubleArray {
        if (kotlin.math.abs(gainDb) < 0.05) {
            return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0)
        }
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * freqHz / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cs
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cs
        val a2 = 1.0 - alpha / a

        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun computeLowShelfEq(
        freqHz: Double,
        sampleRate: Double,
        gainDb: Double
    ): DoubleArray {
        if (kotlin.math.abs(gainDb) < 0.05) {
            return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0)
        }
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * freqHz / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val sqrtA = kotlin.math.sqrt(a)
        val alpha = sn / 2.0 * kotlin.math.sqrt((a + 1.0 / a) * (1.0 / 0.707 - 1.0) + 2.0)

        val b0 = a * ((a + 1.0) - (a - 1.0) * cs + 2.0 * sqrtA * alpha)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cs)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cs - 2.0 * sqrtA * alpha)
        val a0 = (a + 1.0) + (a - 1.0) * cs + 2.0 * sqrtA * alpha
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cs)
        val a2 = (a + 1.0) + (a - 1.0) * cs - 2.0 * sqrtA * alpha

        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}
