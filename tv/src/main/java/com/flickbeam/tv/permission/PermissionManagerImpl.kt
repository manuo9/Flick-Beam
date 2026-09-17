package com.flickbeam.tv.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The real [PermissionManager] — keeps all the Android-version checks in one place. */
@Singleton
class PermissionManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionManager {

    override fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun storageAccessRequest(): StorageAccessRequest {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StorageAccessRequest.ManageAllFiles(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        } else {
            StorageAccessRequest.RuntimePermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun hasInstallPackagesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun installPackagesSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
    }
}
