package com.flickbeam.tv.permission

import android.content.Intent

/**
 * All permission checks and requests live behind this one interface, so the rest of the
 * app doesn't deal with Android's version-specific permission APIs. It's an interface so
 * it can be faked in tests.
 */
interface PermissionManager {

    /** Can the app browse device storage right now? */
    fun hasStorageAccess(): Boolean

    /** What to ask for to get storage access; the screen decides how to launch it. */
    fun storageAccessRequest(): StorageAccessRequest

    /** Is the app allowed to install APKs ("install unknown apps")? */
    fun hasInstallPackagesPermission(): Boolean

    /** The settings screen where the user turns on "install unknown apps". */
    fun installPackagesSettingsIntent(): Intent
}

/**
 * How to ask for storage access:
 * - [ManageAllFiles]: Android 11+ opens a system settings screen.
 * - [RuntimePermission]: older versions show a normal permission prompt.
 */
sealed interface StorageAccessRequest {
    data class ManageAllFiles(val intent: Intent) : StorageAccessRequest
    data class RuntimePermission(val permission: String) : StorageAccessRequest
}
