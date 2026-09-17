package com.flickbeam.remote.network

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

/**
 * Serves one or more picked files over HTTP so the TV can stream them without a full
 * transfer. Each item is reachable at "/<index>"; supports HTTP Range requests, which
 * ExoPlayer uses to seek video/audio smoothly.
 */
class PhoneMediaServer(
    private val context: Context,
    private val items: List<Item>,
) : NanoHTTPD(0) {

    data class Item(val uri: Uri, val size: Long, val mimeType: String)

    override fun serve(session: IHTTPSession): Response {
        val index = session.uri.trim('/').substringBefore('/').toIntOrNull() ?: 0
        val item = items.getOrNull(index) ?: return notFound()
        val range = session.headers["range"]
        return try {
            if (range != null && item.size > 0) servePartial(item, range) else serveFull(item)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "error")
        }
    }

    private fun serveFull(item: Item): Response {
        val input = openStream(item) ?: return notFound()
        return newFixedLengthResponse(
            Response.Status.OK,
            item.mimeType,
            input,
            if (item.size > 0) item.size else -1,
        ).apply { addHeader("Accept-Ranges", "bytes") }
    }

    private fun servePartial(item: Item, range: String): Response {
        val spec = range.substringAfter("bytes=", "")
        val dash = spec.indexOf('-')
        val start = spec.substring(0, dash.coerceAtLeast(0)).toLongOrNull()
            ?.coerceIn(0, item.size - 1) ?: 0L
        val end = spec.substring(dash + 1).toLongOrNull()?.coerceIn(start, item.size - 1)
            ?: (item.size - 1)
        val length = end - start + 1

        val input = openStream(item) ?: return notFound()
        skipFully(input, start)
        return newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, item.mimeType, input, length)
            .apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Range", "bytes $start-$end/${item.size}")
            }
    }

    private fun openStream(item: Item): InputStream? = context.contentResolver.openInputStream(item.uri)

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() < 0) {
                break
            } else {
                remaining--
            }
        }
    }
}
