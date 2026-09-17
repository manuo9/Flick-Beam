package com.flickbeam.tv.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens a file in another app. It shares the file through a FileProvider (Android needs a
 * content:// link, not a raw path) and picks the app based on the file's type.
 */
object FileOpener {

    /** Tries to open [file]. Returns false if no app can handle it. */
    fun open(context: Context, file: File): Boolean {
        val intent = viewIntent(context, file)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Always shows the system app picker, even if the file type has a default handler
     * (e.g. our own player) — for choosing a specific other app, like VLC, on purpose.
     */
    fun openWithChooser(context: Context, file: File, title: String): Boolean {
        val intent = Intent.createChooser(viewIntent(context, file), title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun viewIntent(context: Context, file: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Works out the file's type from its extension. */
    private fun mimeTypeOf(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }
}
