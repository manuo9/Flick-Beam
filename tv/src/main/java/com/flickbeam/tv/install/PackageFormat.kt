package com.flickbeam.tv.install

import java.io.File

/**
 * The installable package formats we support. Archives (.apks, .apkm, .xapk) are
 * all just ZIPs holding a base APK plus config split APKs.
 */
enum class PackageFormat {
    APK,
    ARCHIVE;

    companion object {
        fun detect(file: File): PackageFormat? {
            val name = file.name.lowercase()
            return when {
                name.endsWith(".apk") -> APK
                name.endsWith(".apks") ||
                    name.endsWith(".apkm") ||
                    name.endsWith(".xapk") -> ARCHIVE
                else -> null
            }
        }

        fun isInstallable(name: String): Boolean {
            val n = name.lowercase()
            return n.endsWith(".apk") || n.endsWith(".apks") ||
                n.endsWith(".apkm") || n.endsWith(".xapk")
        }
    }
}
