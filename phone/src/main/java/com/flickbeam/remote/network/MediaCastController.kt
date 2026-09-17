package com.flickbeam.remote.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.flickbeam.shared.LocalNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** The URL the TV should stream from, plus the title and media type. */
data class CastInfo(val url: String, val title: String, val mimeType: String)

/** What to tell the TV to do with a cast. */
sealed interface CastResult {
    /** A single video or audio file. */
    data class Single(val info: CastInfo) : CastResult

    /** A photo slideshow (one or more images). */
    data class Slideshow(val urls: List<String>) : CastResult
}

/**
 * Hosts picked files over the local network so the TV can stream them. Only one cast
 * runs at a time — starting a new one replaces the old server. Selecting images casts
 * a slideshow; a single video/audio casts to the player.
 */
@Singleton
class MediaCastController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var server: PhoneMediaServer? = null

    fun cast(uris: List<Uri>): CastResult? {
        stop()
        if (uris.isEmpty()) return null

        val images = uris.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
        val slideshow = images.isNotEmpty()
        val toServe = if (slideshow) images else listOf(uris.first())

        val items = toServe.map { uri ->
            val (name, size) = queryNameAndSize(uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            ServeItem(uri, name, size, mime)
        }

        val newServer = PhoneMediaServer(context, items.map { PhoneMediaServer.Item(it.uri, it.size, it.mime) })
        return try {
            newServer.start()
            server = newServer
            val host = LocalNetwork.wifiIpv4() ?: run { stop(); return null }
            val infos = items.mapIndexed { index, item ->
                val path = URLEncoder.encode(item.name, "UTF-8")
                CastInfo(
                    url = "http://$host:${newServer.listeningPort}/$index/$path",
                    title = item.name,
                    mimeType = item.mime,
                )
            }
            if (slideshow) CastResult.Slideshow(infos.map { it.url }) else CastResult.Single(infos.first())
        } catch (e: Exception) {
            stop()
            null
        }
    }

    fun stop() {
        server?.let { runCatching { it.stop() } }
        server = null
    }

    private data class ServeItem(val uri: Uri, val name: String, val size: Long, val mime: String)

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
