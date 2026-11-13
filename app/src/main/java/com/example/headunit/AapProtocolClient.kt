package com.example.headunit

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Auto Protocol (AAP) Headunit Receiver & MediaCodec H.264 / AudioTrack PCM engine.
 * Supports Self Mode (127.0.0.1:5277), Wi-Fi TCP Server (:5277), Wi-Fi Client, and USB AOAP.
 */
class AapProtocolClient(
    private val usbManager: AoapUsbManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<AapConnectionStatus>(AapConnectionStatus.Idle)
    val status: StateFlow<AapConnectionStatus> = _status.asStateFlow()

    private val _localIpAddress = MutableStateFlow(resolveLocalIpAddress())
    val localIpAddress: StateFlow<String> = _localIpAddress.asStateFlow()

    private var connectionJob: Job? = null
    private var activeServerSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var activeUsbHandle: AoapUsbManager.AoapStreamHandle? = null
    private var activeOutput: OutputStream? = null

    private var surface: Surface? = null
    private var videoDecoder: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private val decoderConfigured = AtomicBoolean(false)

    private var currentSettings: HeadunitSettings = HeadunitSettings()

    fun attachSurface(newSurface: Surface?) {
        surface = newSurface
        if (newSurface == null) {
            releaseMediaDecoders()
        }
    }

    fun refreshNetworkInfo() {
        _localIpAddress.value = resolveLocalIpAddress()
    }

    fun start(settings: HeadunitSettings) {
        stop()
        currentSettings = settings
        refreshNetworkInfo()

        connectionJob = scope.launch {
            try {
                when (settings.connectionMode) {
                    HurConnectionMode.SELF_MODE -> {
                        connectTcpClient("127.0.0.1", settings.port, "Self Mode (127.0.0.1)", settings)
                    }
                    HurConnectionMode.WIFI_CLIENT -> {
                        val host = settings.clientHostIp.trim().ifEmpty { "127.0.0.1" }
                        connectTcpClient(host, settings.port, "Wi-Fi ($host)", settings)
                    }
                    HurConnectionMode.WIFI_SERVER -> {
                        startTcpServer(settings.port, settings)
                    }
                    HurConnectionMode.USB_AOAP -> {
                        connectUsbAoap(settings)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    _status.value = AapConnectionStatus.Error(
                        title = "خطا در ارتباط گیرنده اندروید اتو",
                        detail = e.localizedMessage ?: "برقراری اتصال با Headunit Server ممکن نشد."
                    )
                }
            } finally {
                cleanupTransport()
            }
        }
    }

    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        cleanupTransport()
        releaseMediaDecoders()
        _status.value = AapConnectionStatus.Idle
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private suspend fun startTcpServer(port: Int, settings: HeadunitSettings) {
        val localIp = resolveLocalIpAddress()
        _localIpAddress.value = localIp
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(port))
        activeServerSocket = server
        _status.value = AapConnectionStatus.Listening(localIp, port)

        val clientSocket = server.accept()
        activeSocket = clientSocket
        clientSocket.tcpNoDelay = true
        val remoteIp = clientSocket.inetAddress?.hostAddress ?: "Phone"
        runAapSession(
            transportName = "Wi-Fi ($remoteIp)",
            input = clientSocket.getInputStream(),
            output = clientSocket.getOutputStream(),
            settings = settings
        )
    }

    private suspend fun connectTcpClient(
        host: String,
        port: Int,
        transportName: String,
        settings: HeadunitSettings
    ) {
        _status.value = AapConnectionStatus.Connecting("$host:$port")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 5_000)
            socket.tcpNoDelay = true
            activeSocket = socket
            runAapSession(
                transportName = transportName,
                input = socket.getInputStream(),
                output = socket.getOutputStream(),
                settings = settings
            )
        } catch (e: Exception) {
            val guidance = if (host == "127.0.0.1") {
                "سرور Headunit اندروید اتو روی پورت $port فعال نیست. در موبایل خود وارد تنظیمات Android Auto ← Developer Settings شوید و «Start head unit server» را فعال کنید، یا در حالت بی‌سیم IP گوشی را وارد کنید."
            } else {
                "امکان اتصال به $host:$port وجود نداشت. از اتصال به یک شبکه وای‌فای/هات‌اسپات مشترک و فعال بودن Head Unit Server اطمینان حاصل کنید."
            }
            _status.value = AapConnectionStatus.Error(
                title = "عدم پاسخ از $host:$port",
                detail = guidance
            )
        }
    }

    private suspend fun connectUsbAoap(settings: HeadunitSettings) {
        _status.value = AapConnectionStatus.Connecting("USB AOAP")
        usbManager.refreshDevices()
        val handle = usbManager.openAoapStream()
        if (handle == null) {
            _status.value = AapConnectionStatus.Error(
                title = "گوشی سازگار با AOAP متصل نیست",
                detail = "گوشی اندرویدی را با کابل USB OTG متصل کنید، مجوز USB را تأیید نمایید و سپس دوباره تلاش کنید."
            )
            return
        }
        activeUsbHandle = handle
        runAapSession(
            transportName = "USB (${handle.label})",
            input = handle.inputStream,
            output = handle.outputStream,
            settings = settings
        )
    }

    private fun runAapSession(
        transportName: String,
        input: InputStream,
        output: OutputStream,
        settings: HeadunitSettings
    ) {
        activeOutput = output
        _status.value = AapConnectionStatus.Handshake("ارسال نسخه پروتکل AAP 1.6…")

        // Send AAP Version Request (Channel 0, Flags 0x03, MessageId 0x0001, major=1, minor=6)
        val versionPayload = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
            .putShort(MSG_VERSION_REQUEST.toShort())
            .putShort(1)
            .putShort(6)
            .array()
        writeAapFrame(output, CHANNEL_CONTROL, FLAG_ENCRYPTED_OR_SINGLE, versionPayload)

        val dataIn = DataInputStream(input)
        var totalBytes = 0L
        var videoFrames = 0L
        var protocolVersion = "AAP 1.6"

        while (scope.isActive) {
            val header = ByteArray(4)
            try {
                dataIn.readFully(header)
            } catch (_: EOFException) {
                break
            }
            val channel = header[0].toInt() and 0xFF
            val flags = header[1].toInt() and 0xFF
            val length = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)

            if (length <= 0 || length > MAX_AAP_FRAME_BYTES) {
                throw IllegalStateException("بسته نامعتبر در کانال $channel (طول $length)")
            }

            val payload = ByteArray(length)
            dataIn.readFully(payload)
            totalBytes += (4 + length)

            when (channel) {
                CHANNEL_CONTROL -> {
                    val msgId = if (payload.size >= 2) {
                        ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    } else 0
                    if (msgId == MSG_VERSION_RESPONSE && payload.size >= 6) {
                        val major = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                        val minor = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
                        protocolVersion = "AAP $major.$minor"
                        _status.value = AapConnectionStatus.Handshake("توافق کانال تصویر ${settings.resolution.width}×${settings.resolution.height}…")
                    } else if (msgId == MSG_PING_REQUEST) {
                        val pong = payload.copyOf()
                        if (pong.size >= 2) {
                            pong[0] = (MSG_PING_RESPONSE shr 8).toByte()
                            pong[1] = (MSG_PING_RESPONSE and 0xFF).toByte()
                        }
                        writeAapFrame(output, CHANNEL_CONTROL, flags, pong)
                    }
                }
                CHANNEL_VIDEO -> {
                    if (payload.size > 2) {
                        feedH264Frame(payload, settings)
                        videoFrames += 1
                    }
                }
                CHANNEL_AUDIO_MEDIA, CHANNEL_AUDIO_GUIDANCE -> {
                    if (payload.size > 2) {
                        playPcmAudio(payload, 2, payload.size - 2)
                    }
                }
            }

            _status.value = AapConnectionStatus.Streaming(
                transport = transportName,
                protocolVersion = protocolVersion,
                width = settings.resolution.width,
                height = settings.resolution.height,
                fps = settings.targetFps,
                bytesReceived = totalBytes,
                videoFrames = videoFrames
            )
        }
    }

    private fun feedH264Frame(frame: ByteArray, settings: HeadunitSettings) {
        val targetSurface = surface ?: return
        if (!targetSurface.isValid) return

        if (decoderConfigured.compareAndSet(false, true)) {
            runCatching {
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    settings.resolution.width,
                    settings.resolution.height
                ).apply {
                    setInteger(MediaFormat.KEY_FRAME_RATE, settings.targetFps)
                }
                val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                codec.configure(format, targetSurface, null, 0)
                codec.start()
                videoDecoder = codec
            }.onFailure {
                decoderConfigured.set(false)
            }
        }

        val codec = videoDecoder ?: return
        runCatching {
            val inputIdx = codec.dequeueInputBuffer(5_000L)
            if (inputIdx >= 0) {
                val buffer = codec.getInputBuffer(inputIdx)
                if (buffer != null) {
                    buffer.clear()
                    val offset = 2.coerceAtMost(frame.size)
                    val len = (frame.size - offset).coerceAtMost(buffer.remaining())
                    buffer.put(frame, offset, len)
                    codec.queueInputBuffer(inputIdx, 0, len, System.nanoTime() / 1000L, 0)
                }
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outIdx = codec.dequeueOutputBuffer(bufferInfo, 0L)
            while (outIdx >= 0) {
                codec.releaseOutputBuffer(outIdx, true)
                outIdx = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        }
    }

    private fun playPcmAudio(data: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        if (audioTrack == null) {
            val sampleRate = 48_000
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        }
        audioTrack?.write(data, offset, length)
    }

    fun sendTouchEvent(action: Int, normalizedX: Float, normalizedY: Float) {
        val output = activeOutput ?: return
        val x = (normalizedX.coerceIn(0f, 1f) * currentSettings.resolution.width).toInt()
        val y = (normalizedY.coerceIn(0f, 1f) * currentSettings.resolution.height).toInt()
        scope.launch {
            runCatching {
                val payload = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN)
                    .putShort(MSG_INPUT_EVENT.toShort())
                    .putInt(action)
                    .putInt(x)
                    .putInt(y)
                    .array()
                writeAapFrame(output, CHANNEL_INPUT, FLAG_ENCRYPTED_OR_SINGLE, payload)
            }
        }
    }

    fun sendSteeringKey(keyCode: Int) {
        val output = activeOutput ?: return
        scope.launch {
            runCatching {
                val payload = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
                    .putShort(MSG_KEY_EVENT.toShort())
                    .putInt(keyCode)
                    .array()
                writeAapFrame(output, CHANNEL_INPUT, FLAG_ENCRYPTED_OR_SINGLE, payload)
            }
        }
    }

    private fun writeAapFrame(output: OutputStream, channel: Int, flags: Int, payload: ByteArray) {
        val frame = ByteArray(4 + payload.size)
        frame[0] = (channel and 0xFF).toByte()
        frame[1] = (flags and 0xFF).toByte()
        frame[2] = ((payload.size ushr 8) and 0xFF).toByte()
        frame[3] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, 4, payload.size)
        synchronized(output) {
            output.write(frame)
            output.flush()
        }
    }

    private fun cleanupTransport() {
        activeOutput = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        runCatching { activeServerSocket?.close() }
        activeServerSocket = null
        runCatching { activeUsbHandle?.close() }
        activeUsbHandle = null
    }

    private fun releaseMediaDecoders() {
        decoderConfigured.set(false)
        runCatching {
            videoDecoder?.stop()
            videoDecoder?.release()
        }
        videoDecoder = null
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
    }

    private fun resolveLocalIpAddress(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching "127.0.0.1"
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return@runCatching addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    companion object {
        private const val CHANNEL_CONTROL = 0
        private const val CHANNEL_INPUT = 1
        private const val CHANNEL_VIDEO = 3
        private const val CHANNEL_AUDIO_MEDIA = 4
        private const val CHANNEL_AUDIO_GUIDANCE = 5

        private const val FLAG_ENCRYPTED_OR_SINGLE = 0x03
        private const val MSG_VERSION_REQUEST = 0x0001
        private const val MSG_VERSION_RESPONSE = 0x0002
        private const val MSG_PING_REQUEST = 0x000B
        private const val MSG_PING_RESPONSE = 0x000C
        private const val MSG_INPUT_EVENT = 0x8001
        private const val MSG_KEY_EVENT = 0x8002
        private const val MAX_AAP_FRAME_BYTES = 1024 * 1024
    }
}
