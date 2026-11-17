package com.example.player

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioVoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun startRecording(): Result<Unit> {
        stopSilently()
        val file = File(context.cacheDir, "ai_voice_query_${System.currentTimeMillis()}.m4a")
        return runCatching {
            val mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(64_000)
                setAudioChannels(1)
                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
        }.onFailure {
            runCatching { file.delete() }
            recorder = null
            outputFile = null
        }
    }

    fun sampleAmplitudeNormalized(): Float {
        val rec = recorder ?: return 0f
        return runCatching {
            (rec.maxAmplitude.toFloat() / 32767f).coerceIn(0f, 1f)
        }.getOrDefault(0f)
    }

    fun stopAndReadBytes(): Result<ByteArray> {
        val rec = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (rec == null || file == null) {
            return Result.failure(IllegalStateException("ضبط صوت فعال نیست."))
        }
        return runCatching {
            try {
                rec.stop()
            } finally {
                runCatching { rec.reset() }
                runCatching { rec.release() }
            }
            if (!file.exists() || file.length() == 0L) {
                throw IllegalStateException("صدایی ضبط نشد؛ لطفاً نزدیک‌تر به میکروفون صحبت کنید.")
            }
            if (file.length() > MAX_INLINE_AUDIO_BYTES) {
                throw IllegalStateException("حجم فایل صوتی بیش از حد مجاز است.")
            }
            file.readBytes()
        }.also {
            runCatching { file.delete() }
        }
    }

    fun cancelRecording() {
        stopSilently()
    }

    private fun stopSilently() {
        val rec = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.reset() }
            runCatching { rec.release() }
        }
        if (file != null) {
            runCatching { file.delete() }
        }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    companion object {
        const val MIME_TYPE_MP4 = "audio/mp4"
        const val MAX_DURATION_MS = 15_000
        private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024L
    }
}
