package com.flickbeam.tv.repository

import com.flickbeam.tv.data.FileDataSource
import com.flickbeam.tv.model.FileEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One place the rest of the app goes for file operations. */
@Singleton
class FileRepository @Inject constructor(
    private val fileDataSource: FileDataSource,
) {
    fun rootDirectory(): File = fileDataSource.rootDirectory()

    fun listEntries(directory: File): List<FileEntry> = fileDataSource.list(directory)

    /** Deletes a file or folder (recursively). Returns true on success. */
    fun delete(entry: FileEntry): Boolean = fileDataSource.delete(entry.file)

    /** Renames a file or folder within its current directory. Returns true on success. */
    fun rename(entry: FileEntry, newName: String): Boolean =
        fileDataSource.rename(entry.file, newName)

    /** Copies into [destinationDir], renaming on a clash. Returns the name used, or null. */
    fun copy(entry: FileEntry, destinationDir: File): String? =
        fileDataSource.copy(entry.file, destinationDir)

    /** Moves into [destinationDir], renaming on a clash. Returns the name used, or null. */
    fun move(entry: FileEntry, destinationDir: File): String? =
        fileDataSource.move(entry.file, destinationDir)

    /** True if moving [entry] into [destinationDir] would place a folder inside itself. */
    fun isMoveIntoOwnDescendant(entry: FileEntry, destinationDir: File): Boolean =
        fileDataSource.isMoveIntoOwnDescendant(entry.file, destinationDir)
}
