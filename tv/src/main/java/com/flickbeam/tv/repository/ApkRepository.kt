package com.flickbeam.tv.repository

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.flickbeam.tv.install.InstallInfo
import com.flickbeam.tv.install.PackageArchive
import com.flickbeam.tv.install.PackageFormat
import com.flickbeam.tv.install.SplitSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

private const val INSTALL_STATUS_ACTION = "com.flickbeam.tv.ACTION_INSTALL_APK_STATUS"

/**
 * Installs apps using Android's PackageInstaller. Handles plain .apk files and split
 * archives (.apks/.apkm/.xapk) the same way: a plain apk is just a bundle of one split.
 * Archives have their device-appropriate splits selected and written into one session.
 */
@Singleton
class ApkRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Read a package's details without installing it (for the inspect screen).
     * Blocking file I/O — call off the main thread. Returns null if it can't be read.
     */
    fun inspect(file: File): InstallInfo? = when (PackageFormat.detect(file)) {
        PackageFormat.APK -> readInfo(file, architecture = "any", splitCount = 1)

        PackageFormat.ARCHIVE -> runCatching {
            val entries = PackageArchive.apkEntries(file)
            val baseEntry = PackageArchive.baseEntry(entries) ?: return@runCatching null
            val selected = SplitSelector.select(context, entries)
            val abis = SplitSelector.architectures(entries)
            val baseTemp = File(context.cacheDir, "inspect-${System.currentTimeMillis()}.apk")
            try {
                PackageArchive.extractEntry(file, baseEntry, baseTemp)
                readInfo(
                    apk = baseTemp,
                    architecture = if (abis.isEmpty()) "any" else abis.joinToString(", "),
                    splitCount = selected.size,
                )
            } finally {
                baseTemp.delete()
            }
        }.getOrNull()

        null -> null
    }

    private fun readInfo(apk: File, architecture: String, splitCount: Int): InstallInfo? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_PERMISSIONS)
            ?: return null
        val appInfo = info.applicationInfo?.apply {
            // Needed so loadLabel can read the label out of the (uninstalled) apk file.
            sourceDir = apk.absolutePath
            publicSourceDir = apk.absolutePath
        }
        val appName = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: info.packageName
        return InstallInfo(
            appName = appName,
            packageName = info.packageName ?: "",
            versionName = info.versionName ?: "",
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo?.minSdkVersion else null,
            targetSdk = appInfo?.targetSdkVersion,
            architecture = architecture,
            splitCount = splitCount,
            permissions = info.requestedPermissions?.toList() ?: emptyList(),
        )
    }

    /**
     * Installs [file] (any supported format). The system may show its own confirm
     * screen. The result comes back later through [onResult]. Blocking — call off
     * the main thread.
     */
    fun install(file: File, onResult: (success: Boolean, message: String) -> Unit) {
        when (PackageFormat.detect(file)) {
            PackageFormat.APK -> runSession(onResult) { session ->
                writeSplit(session, "0.apk", file.length()) { file.inputStream() }
                1
            }

            PackageFormat.ARCHIVE -> {
                val selected = runCatching {
                    SplitSelector.select(context, PackageArchive.apkEntries(file))
                }.getOrNull().orEmpty()
                if (selected.isEmpty()) {
                    onResult(false, "No installable APKs found in ${file.name}")
                    return
                }
                runSession(onResult) { session ->
                    var written = 0
                    ZipFile(file).use { zip ->
                        selected.forEachIndexed { index, entryName ->
                            val entry = zip.getEntry(entryName) ?: return@forEachIndexed
                            writeSplit(session, "$index.apk", entry.size) { zip.getInputStream(entry) }
                            written++
                        }
                    }
                    written
                }
            }

            null -> onResult(false, "Unsupported file: ${file.name}")
        }
    }

    // Runs [writeSplits] against a fresh session, then commits. [writeSplits] returns how
    // many APKs it wrote; if none, the session is abandoned.
    private fun runSession(
        onResult: (Boolean, String) -> Unit,
        writeSplits: (PackageInstaller.Session) -> Int,
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = try {
            packageInstaller.createSession(params)
        } catch (e: Exception) {
            onResult(false, "Could not start install: ${e.message}")
            return
        }
        val session = packageInstaller.openSession(sessionId)

        try {
            val count = writeSplits(session)
            if (count == 0) {
                session.abandon()
                onResult(false, "Nothing to install")
                return
            }

            val receiver = InstallResultReceiver(context, onResult)
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(INSTALL_STATUS_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            val statusIntent = Intent(INSTALL_STATUS_ACTION).apply { setPackage(context.packageName) }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                statusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            onResult(false, "Install failed: ${e.message}")
        } finally {
            session.close()
        }
    }

    private fun writeSplit(
        session: PackageInstaller.Session,
        name: String,
        length: Long,
        openInput: () -> InputStream,
    ) {
        session.openWrite(name, 0, length).use { output ->
            openInput().use { input -> input.copyTo(output) }
            session.fsync(output)
        }
    }

    private class InstallResultReceiver(
        private val appContext: Context,
        private val onResult: (Boolean, String) -> Unit,
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // The system wants the user to confirm — show its dialog.
                    @Suppress("DEPRECATION")
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    confirmIntent?.let { appContext.startActivity(it) }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    onResult(true, "Installed successfully")
                    unregisterSafely(context)
                }

                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: "Install failed"
                    onResult(false, message)
                    unregisterSafely(context)
                }
            }
        }

        private fun unregisterSafely(context: Context) {
            try {
                context.unregisterReceiver(this)
            } catch (_: IllegalArgumentException) {
                // Already unregistered — safe to ignore.
            }
        }
    }
}
