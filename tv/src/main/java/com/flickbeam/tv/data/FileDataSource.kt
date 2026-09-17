package com.flickbeam.tv.data

import android.os.Environment
import com.flickbeam.tv.model.FileEntry
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and changes files on disk using java.io.File. */
@Singleton
class FileDataSource @Inject constructor() {

    /** The shared external storage root (e.g. /storage/emulated/0). */
    fun rootDirectory(): File = Environment.getExternalStorageDirectory()

    /** Lists a directory's contents, folders first then files, both alphabetically. */
    fun list(directory: File): List<FileEntry> {
        val children = directory.listFiles() ?: return emptyList()
        return children
            .map { FileEntry(it) }
            .sortedWith(
                compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) }),
            )
    }

    fun delete(target: File): Boolean = target.deleteRecursively()

    fun rename(target: File, newName: String): Boolean {
        val destination = File(target.parentFile, newName)
        if (destination.exists()) return false
        return target.renameTo(destination)
    }

    /**
     * Copies [source] into [destinationDir]. If the name is taken, adds "(2)", "(3)", ...
     * Returns the name used, or null if it failed.
     */
    fun copy(source: File, destinationDir: File): String? {
        return try {
            val destination = uniqueDestination(destinationDir, source.name)
            source.copyRecursively(destination, overwrite = false)
            destination.name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Moves [source] into [destinationDir], renaming on a name clash. Returns the name
     * used, or null if it failed (including trying to move a folder inside itself).
     */
    fun move(source: File, destinationDir: File): String? {
        // Don't allow moving a folder inside itself.
        if (isMoveIntoOwnDescendant(source, destinationDir)) return null
        val destination = uniqueDestination(destinationDir, source.name)
        // A rename is instant on the same drive; otherwise copy then delete.
        if (source.renameTo(destination)) return destination.name
        return try {
            source.copyRecursively(destination, overwrite = false)
            source.deleteRecursively()
            destination.name
        } catch (e: Exception) {
            null
        }
    }

    /** Finds a free name: [name], or "name (2)", "name (3)", ... before the extension. */
    private fun uniqueDestination(destinationDir: File, name: String): File {
        val first = File(destinationDir, name)
        if (!first.exists()) return first

        val dotIndex = name.lastIndexOf('.')
        val hasExtension = dotIndex > 0 // > 0 so a file like ".config" keeps its name
        val base = if (hasExtension) name.substring(0, dotIndex) else name
        val extension = if (hasExtension) name.substring(dotIndex) else ""

        var counter = 2
        var candidate = File(destinationDir, "$base ($counter)$extension")
        while (candidate.exists()) {
            counter++
            candidate = File(destinationDir, "$base ($counter)$extension")
        }
        return candidate
    }

    /** True if [destinationDir] is [source] or sits inside it (an illegal move). */
    fun isMoveIntoOwnDescendant(source: File, destinationDir: File): Boolean {
        if (!source.isDirectory) return false
        return try {
            val sourceCanonical = source.canonicalFile
            var dir: File? = destinationDir.canonicalFile
            while (dir != null) {
                if (dir == sourceCanonical) return true
                dir = dir.parentFile
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
