package com.flickbeam.remote.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.flickbeam.shared.Endpoints
import com.flickbeam.shared.TOKEN_HEADER
import com.flickbeam.shared.UPLOAD_NAME_HEADER
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads a picked file to the TV over HTTP (the data channel), streaming the
 * bytes with a known Content-Length and reporting progress as a percentage.
 * Returns the name the TV saved it as.
 */
@Singleton
class FileSender @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pairingStore: PhonePairingStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Per-write timeout (not total), so big files are fine but a dropped
        // connection fails fast instead of hanging.
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The file's display name, for showing in the send list before it uploads. */
    fun displayName(uri: Uri): String = queryNameAndSize(uri).first

    /** [onProgress] is called with 0..100 as the upload proceeds. */
    fun send(target: DiscoveredTv, uri: Uri, onProgress: (Int) -> Unit): Result<String> {
        val (name, querySize) = queryNameAndSize(uri)
        var tempFile: File? = null
        return try {
            val size: Long
            val openStream: () -> InputStream
            if (querySize >= 0) {
                size = querySize
                openStream = {
                    context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Can't open the selected file")
                }
            } else {
                // Size unknown: materialize to cache so we can send a Content-Length.
                tempFile = copyToCache(uri)
                val cached = tempFile
                size = cached.length()
                openStream = { cached.inputStream() }
            }

            val requestBuilder = Request.Builder()
                .url("http://${target.host}:${target.port}${Endpoints.UPLOAD}/${UUID.randomUUID()}")
                .header(UPLOAD_NAME_HEADER, URLEncoder.encode(name, "UTF-8"))
                .put(progressBody(openStream, size, onProgress))

            // Uploads require the pairing token, same as the control channel.
            pairingStore.token(target.deviceId)?.let {
                requestBuilder.header(TOKEN_HEADER, it)
            }

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(IOException("TV returned ${response.code}"))
                } else {
                    onProgress(100)
                    val saved = response.body?.string()?.takeIf { it.isNotBlank() } ?: name
                    Result.success(saved)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempFile?.delete()
        }
    }

    private fun progressBody(
        openStream: () -> InputStream,
        size: Long,
        onProgress: (Int) -> Unit,
    ): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = size
        override fun writeTo(sink: BufferedSink) {
            openStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var sent = 0L
                var lastPercent = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    sent += read
                    if (size > 0) {
                        val percent = ((sent * 100) / size).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }
    }

    private fun copyToCache(uri: Uri): File {
        val temp = File(context.cacheDir, "upload-${UUID.randomUUID()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Can't open the selected file")
        return temp
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }
}
