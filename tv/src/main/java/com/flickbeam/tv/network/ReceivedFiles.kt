package com.flickbeam.tv.network

import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves received files into Downloads/FlickBeam on shared storage (the app already
 * holds all-files access), auto-renaming on name collisions.
 *
 * The bytes are written to a temporary ".part" file first and only renamed to the
 * final name once the whole file has arrived. A dropped or cut-short transfer
 * throws and deletes the temp file, so no broken half-files are ever left behind.
 */
@Singleton
class ReceivedFiles @Inject constructor() : IncomingFiles {

    /** Delete leftover ".part" temp files from transfers that were cut short. */
    fun cleanupPartials() {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FlickBeam",
        )
        val partials = dir.listFiles { file -> file.name.endsWith(".part") } ?: return
        partials.forEach { runCatching { it.delete() } }
    }

    override fun save(name: String, input: InputStream, length: Long, onProgress: (Int) -> Unit): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FlickBeam",
        )
        if (!dir.exists()) dir.mkdirs()

        val temp = File(dir, "upload-${UUID.randomUUID()}.part")
        try {
            FileOutputStream(temp).use { out ->
                if (length >= 0) {
                    if (!copyExactly(input, out, length, onProgress)) {
                        throw IOException("Transfer ended before all bytes arrived")
                    }
                } else {
                    input.copyTo(out)
                }
            }
            val target = uniqueFile(dir, sanitize(name))
            if (!temp.renameTo(target)) throw IOException("Could not save $name")
            return target.name
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    // Read exactly [length] bytes: the socket stays open (keep-alive), so we must
    // not read past the body or we'd block forever. Returns true only if all the
    // expected bytes arrived; false means the stream ended early (incomplete).
    private fun copyExactly(
        input: InputStream,
        out: FileOutputStream,
        length: Long,
        onProgress: (Int) -> Unit,
    ): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = length
        var written = 0L
        var lastPercent = -1
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            out.write(buffer, 0, read)
            remaining -= read
            written += read
            val percent = ((written * 100) / length).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(percent)
            }
        }
        return remaining == 0L
    }

    private fun sanitize(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return cleaned.ifEmpty { "received_file" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }
}
