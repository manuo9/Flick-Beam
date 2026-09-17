package com.flickbeam.tv.model

import java.io.File
import java.util.Locale

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
private val VIDEO_EXTENSIONS =
    setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "flv", "wmv", "mpg", "mpeg")
private val AUDIO_EXTENSIONS =
    setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "mid", "amr")
private val SUBTITLE_EXTENSIONS = listOf("srt", "ass", "ssa", "vtt")

/** One row in a folder — a file or a sub-folder. */
data class FileEntry(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val sizeBytes: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val absolutePath: String = file.absolutePath,
) {
    val isApk: Boolean get() = !isDirectory && name.endsWith(".apk", ignoreCase = true)

    /** True for anything we can install: a plain .apk or a split archive (.apks/.apkm/.xapk). */
    val isInstallable: Boolean
        get() = !isDirectory && (
            name.endsWith(".apk", ignoreCase = true) ||
                name.endsWith(".apks", ignoreCase = true) ||
                name.endsWith(".apkm", ignoreCase = true) ||
                name.endsWith(".xapk", ignoreCase = true)
            )

    /** True for image files we can show in the built-in viewer. */
    val isImage: Boolean
        get() = !isDirectory &&
            name.substringAfterLast('.', "").lowercase(Locale.getDefault()) in IMAGE_EXTENSIONS

    /** True for video files we can play in the built-in player. */
    val isVideo: Boolean
        get() = !isDirectory &&
            name.substringAfterLast('.', "").lowercase(Locale.getDefault()) in VIDEO_EXTENSIONS

    /** True for audio files we can play in the built-in player. */
    val isAudio: Boolean
        get() = !isDirectory &&
            name.substringAfterLast('.', "").lowercase(Locale.getDefault()) in AUDIO_EXTENSIONS

    /** A subtitle file sitting next to this video with the same base name, or null. */
    fun sidecarSubtitle(): File? {
        val parent = file.parentFile ?: return null
        val base = name.substringBeforeLast('.')
        return SUBTITLE_EXTENSIONS
            .map { File(parent, "$base.$it") }
            .firstOrNull { it.exists() }
    }

    /** A readable type for the Properties view, like "Folder" or "MP4 file". */
    val typeLabel: String
        get() = when {
            isDirectory -> "Folder"
            name.contains('.') -> "${name.substringAfterLast('.').uppercase(Locale.getDefault())} file"
            else -> "File"
        }
}

/** Turns a byte count into text like "482 KB" or "1.3 GB". */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

/** Turns a timestamp into text like "14 Mar 2026, 21:07". */
fun formatLastModified(millis: Long): String {
    if (millis <= 0L) return "Unknown"
    val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(java.util.Date(millis))
}
