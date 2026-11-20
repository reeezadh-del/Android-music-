package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@OptIn(UnstableApi::class)
class UnblockedOkHttpDataSource(
    private val client: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val client: OkHttpClient,
        private val userAgent: String = DEFAULT_USER_AGENT
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = UnblockedOkHttpDataSource(client, userAgent)
    }

    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseStream: InputStream? = null
    private var opened = false
    private var bytesToRead: Long = C.LENGTH_UNSET.toLong()
    private var bytesRead: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesRead = 0L
        transferInitializing(dataSpec)

        val requestBuilder = Request.Builder()
            .url(dataSpec.uri.toString())
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")

        dataSpec.httpRequestHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val start = dataSpec.position
            val rangeHeader = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                "bytes=$start-${start + dataSpec.length - 1}"
            } else {
                "bytes=$start-"
            }
            requestBuilder.header("Range", rangeHeader)
        }

        val executedResponse = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN
            )
        }

        response = executedResponse
        val body = executedResponse.body
        if (!executedResponse.isSuccessful || body == null) {
            val headers = executedResponse.headers.toMultimap()
            closeConnectionQuietly()
            throw HttpDataSource.InvalidResponseCodeException(
                executedResponse.code,
                executedResponse.message,
                null,
                headers,
                dataSpec,
                ByteArray(0)
            )
        }

        val contentLength = body.contentLength()
        bytesToRead = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            contentLength >= 0L -> contentLength
            else -> C.LENGTH_UNSET.toLong()
        }

        responseStream = body.byteStream()
        opened = true
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val remaining = bytesToRead - bytesRead
            if (remaining <= 0L) return C.RESULT_END_OF_INPUT
        }

        val stream = responseStream ?: return C.RESULT_END_OF_INPUT
        val maxRead = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesToRead - bytesRead).toInt()
        }

        val read = try {
            stream.read(buffer, offset, maxRead)
        } catch (e: IOException) {
            val spec = dataSpec ?: throw HttpDataSource.HttpDataSourceException(
                e,
                DataSpec(Uri.EMPTY),
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_READ
            )
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                spec,
                HttpDataSource.HttpDataSourceException.TYPE_READ
            )
        }

        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        bytesRead += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? {
        return response?.request?.url?.toString()?.let { Uri.parse(it) } ?: dataSpec?.uri
    }

    override fun close() {
        dataSpec = null
        closeConnectionQuietly()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeConnectionQuietly() {
        runCatching { responseStream?.close() }
        runCatching { response?.close() }
        responseStream = null
        response = null
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
