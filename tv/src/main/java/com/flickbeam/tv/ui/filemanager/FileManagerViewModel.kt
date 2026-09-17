package com.flickbeam.tv.ui.filemanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flickbeam.tv.install.InstallInfo
import com.flickbeam.tv.model.FileEntry
import com.flickbeam.tv.permission.PermissionManager
import com.flickbeam.tv.permission.StorageAccessRequest
import com.flickbeam.tv.repository.ApkRepository
import com.flickbeam.tv.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Is the clipboard item waiting to be copied or moved? */
enum class ClipboardMode { COPY, MOVE }

/** Holds the File Manager's state and logic, so the screen just draws what it's told. */
@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val apkRepository: ApkRepository,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    var hasStorageAccess by mutableStateOf(permissionManager.hasStorageAccess())
        private set

    var currentPath by mutableStateOf("")
        private set

    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    // The item waiting to be pasted, and whether it's a copy or a move. Null = nothing
    // on the clipboard. Kept while you move between folders.
    var clipboardItem by mutableStateOf<FileEntry?>(null)
        private set

    var clipboardMode by mutableStateOf<ClipboardMode?>(null)
        private set

    // Name of the item currently being copied, or null when no copy is running. A copy
    // can take a while for a large file, so this stays up for the whole operation —
    // unlike statusMessage, which auto-hides after a few seconds.
    var copyingItemName by mutableStateOf<String?>(null)
        private set

    // Which row the screen should focus once a folder loads: null = the first row; a path
    // = that row (used when Back returns you to the folder you just left).
    var focusTargetPath by mutableStateOf<String?>(null)
        private set

    // Package details to show in the inspect screen, or null if it's closed.
    var inspectInfo by mutableStateOf<InstallInfo?>(null)
        private set

    // The file the inspect screen is about (kept so "Install" knows what to install).
    private var inspectFile: File? = null

    // The folders you've opened; the last one is the current folder.
    private val backStack = ArrayDeque<File>()

    // An APK waiting to install once the user grants the "install unknown apps" permission.
    private var pendingInstallFile: File? = null

    init {
        if (hasStorageAccess) openRoot()
    }

    /** Re-check storage access (call when returning from the system permission screen). */
    fun refreshStorageAccess() {
        hasStorageAccess = permissionManager.hasStorageAccess()
        if (hasStorageAccess && backStack.isEmpty()) openRoot()
    }

    fun storageAccessRequest(): StorageAccessRequest = permissionManager.storageAccessRequest()

    /** The system settings intent for granting install-unknown-apps, for the UI to launch. */
    fun installPackagesSettingsIntent(): android.content.Intent =
        permissionManager.installPackagesSettingsIntent()

    /**
     * Handles pressing OK on a row: open a folder, install an APK, show an image, or open
     * another file in another app (the screen does the actual opening).
     */
    fun onEntrySelected(
        entry: FileEntry,
        onOpenImage: (FileEntry) -> Unit,
        onOpenMedia: (FileEntry) -> Unit,
        onOpenFile: (FileEntry) -> Unit,
    ) {
        when {
            entry.isDirectory -> {
                backStack.addLast(entry.file)
                focusTargetPath = null // entering a folder starts at the top
                reload()
            }

            entry.isInstallable -> inspectPackage(entry.file)

            // Images, videos and audio use our built-in viewers; other files open elsewhere.
            entry.isImage -> onOpenImage(entry)

            entry.isVideo || entry.isAudio -> onOpenMedia(entry)

            else -> onOpenFile(entry)
        }
    }

    /** Called by the UI when no installed app could open the selected file. */
    fun reportNoAppToOpen(entry: FileEntry) {
        statusMessage = "No app found to open ${entry.name}"
    }

    /** Clears the status message (the UI hides it after a delay). */
    fun dismissStatus() {
        statusMessage = null
    }

    /** Resumes a pending install after the user granted install-unknown-apps access. */
    fun onInstallPermissionResult() {
        val apk = pendingInstallFile ?: return
        pendingInstallFile = null
        if (permissionManager.hasInstallPackagesPermission()) {
            installPackage(apk)
        } else {
            statusMessage = "Install cancelled: permission not granted"
        }
    }

    /**
     * Navigates up one level. Returns false if already at the root (the caller should then
     * exit the File Manager back to Home).
     */
    fun navigateUp(): Boolean {
        if (backStack.size > 1) {
            val leaving = backStack.removeLast()
            // Put focus back on the folder we just came out of.
            focusTargetPath = leaving.absolutePath
            reload()
            return true
        }
        return false
    }

    // --- File operations ---

    fun delete(entry: FileEntry) {
        statusMessage = if (fileRepository.delete(entry)) {
            reload()
            "Deleted ${entry.name}"
        } else {
            "Could not delete ${entry.name}"
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        statusMessage = if (fileRepository.rename(entry, newName)) {
            reload()
            "Renamed to $newName"
        } else {
            "Could not rename ${entry.name}"
        }
    }

    // --- Clipboard (Copy/Move) ---

    fun copyToClipboard(entry: FileEntry) {
        clipboardItem = entry
        clipboardMode = ClipboardMode.COPY
        statusMessage = "Copied ${entry.name} — browse to a folder and paste"
    }

    fun moveToClipboard(entry: FileEntry) {
        clipboardItem = entry
        clipboardMode = ClipboardMode.MOVE
        statusMessage = "Ready to move ${entry.name} — browse to a folder and paste"
    }

    fun clearClipboard() {
        clipboardItem = null
        clipboardMode = null
    }

    /**
     * Pastes into the current folder. Copy keeps the clipboard for pasting again; Move
     * clears it once it works. If the name is taken, the item is auto-renamed.
     */
    fun paste() {
        if (copyingItemName != null) return // a copy is already running
        val item = clipboardItem ?: return
        val mode = clipboardMode ?: return
        val destination = backStack.lastOrNull() ?: return

        when (mode) {
            ClipboardMode.COPY -> {
                copyingItemName = item.name
                viewModelScope.launch {
                    val pastedName = withContext(Dispatchers.IO) {
                        fileRepository.copy(item, destination)
                    }
                    copyingItemName = null
                    if (pastedName != null) {
                        reload()
                        statusMessage = if (pastedName == item.name) {
                            "Pasted ${item.name}"
                        } else {
                            "Pasted as $pastedName"
                        }
                    } else {
                        statusMessage = "Could not paste ${item.name}"
                    }
                }
            }

            ClipboardMode.MOVE -> {
                val alreadyHere = runCatching {
                    item.file.parentFile?.canonicalFile == destination.canonicalFile
                }.getOrDefault(false)
                when {
                    fileRepository.isMoveIntoOwnDescendant(item, destination) ->
                        statusMessage = "Can't move a folder into itself or its own subfolder"

                    alreadyHere ->
                        statusMessage = "${item.name} is already in this folder"

                    else -> {
                        val movedName = fileRepository.move(item, destination)
                        if (movedName != null) {
                            // Move clears the clipboard only after a successful move.
                            clearClipboard()
                            reload()
                            statusMessage = if (movedName == item.name) {
                                "Moved ${item.name}"
                            } else {
                                "Moved as $movedName"
                            }
                        } else {
                            statusMessage = "Could not move ${item.name}"
                        }
                    }
                }
            }
        }
    }

    /** Read a package's details and open the inspect screen. */
    private fun inspectPackage(file: File) {
        inspectFile = file
        statusMessage = "Reading ${file.name}…"
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { apkRepository.inspect(file) }
            statusMessage = null
            if (info == null) {
                statusMessage = "Couldn't read ${file.name}"
                inspectFile = null
            } else {
                inspectInfo = info
            }
        }
    }

    /** Close the inspect screen without installing. */
    fun dismissInspect() {
        inspectInfo = null
        inspectFile = null
    }

    /** Confirm install from the inspect screen; asks for permission first if needed. */
    fun confirmInstall(onNeedInstallPermission: () -> Unit) {
        val file = inspectFile ?: return
        inspectInfo = null
        if (permissionManager.hasInstallPackagesPermission()) {
            installPackage(file)
        } else {
            pendingInstallFile = file
            onNeedInstallPermission()
        }
    }

    private fun installPackage(file: File) {
        inspectFile = null
        statusMessage = "Installing ${file.name}…"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                apkRepository.install(file) { success, message ->
                    statusMessage = if (success) "${file.name}: $message" else "Install failed: $message"
                }
            }
        }
    }

    private fun openRoot() {
        backStack.clear()
        backStack.addLast(fileRepository.rootDirectory())
        focusTargetPath = null
        reload()
    }

    private fun reload() {
        val current = backStack.lastOrNull() ?: return
        currentPath = current.absolutePath
        entries = fileRepository.listEntries(current)
    }
}
