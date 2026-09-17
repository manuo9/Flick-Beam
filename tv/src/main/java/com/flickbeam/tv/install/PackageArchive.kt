package com.flickbeam.tv.install

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Reads the APK entries out of a split archive (.apks / .apkm / .xapk). */
object PackageArchive {

    /** Names of every APK entry inside the archive. */
    fun apkEntries(archive: File): List<String> =
        ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                .map { it.name }
                .toList()
        }

    /** The base APK entry (the one carrying the app's manifest), or null if none. */
    fun baseEntry(apkEntries: List<String>): String? {
        val preferred = apkEntries.firstOrNull {
            val n = it.substringAfterLast('/').lowercase()
            n == "base.apk" || n.startsWith("base")
        }
        if (preferred != null) return preferred
        // Otherwise the first entry that isn't a config split (e.g. the xapk package apk).
        return apkEntries.firstOrNull { !it.lowercase().contains("config") } ?: apkEntries.firstOrNull()
    }

    /** Extract one entry to [dest]. */
    fun extractEntry(archive: File, entryName: String, dest: File) {
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(entryName) ?: throw IOException("Missing $entryName")
            zip.getInputStream(entry).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
