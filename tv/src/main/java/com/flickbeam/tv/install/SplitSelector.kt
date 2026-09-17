package com.flickbeam.tv.install

import android.content.Context
import android.os.Build

/**
 * Picks which APK entries of a split archive to actually install on THIS device.
 *
 * Installing every config split (all ABIs, all densities) usually makes the install
 * fail, so we behave like the Play Store: keep the base and feature splits, keep only
 * the ABI split matching the device, keep only the nearest-density split, and keep the
 * rest (language splits, base, etc.). ABI mismatch is the main cause of failed installs,
 * so getting that right is what matters most.
 */
object SplitSelector {

    // Longest first so "x86_64" is matched before "x86".
    private val ABIS = listOf(
        "arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "mips64", "mips",
    )

    private val DENSITY_DPI = mapOf(
        "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
        "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
    )

    /** Returns the subset of [apkEntries] (zip entry names) to install. */
    fun select(context: Context, apkEntries: List<String>): List<String> {
        val deviceAbis = Build.SUPPORTED_ABIS.map { it.lowercase().replace('-', '_') }
        val deviceDensity = context.resources.displayMetrics.densityDpi

        // Best ABI: the first device-preferred ABI that the archive actually has a split for.
        val abiTokens = apkEntries.mapNotNull { abiToken(it) }.toSet()
        val chosenAbi = deviceAbis.firstOrNull { it in abiTokens }

        // Nearest density bucket the archive has a split for.
        val densityTokens = apkEntries.mapNotNull { densityToken(it) }.toSet()
        val chosenDensity = densityTokens.minByOrNull {
            kotlin.math.abs((DENSITY_DPI[it] ?: deviceDensity) - deviceDensity)
        }

        return apkEntries.filter { entry ->
            val abi = abiToken(entry)
            val density = densityToken(entry)
            when {
                abi != null -> abi == chosenAbi
                density != null -> density == chosenDensity
                else -> true // base, feature, language and other splits: always keep
            }
        }
    }

    /** The ABIs the archive carries splits for, for the inspect screen. */
    fun architectures(apkEntries: List<String>): List<String> =
        apkEntries.mapNotNull { abiToken(it) }.distinct()

    private fun abiToken(entry: String): String? {
        if (!isConfigSplit(entry)) return null
        val n = entry.lowercase().replace('-', '_')
        return ABIS.firstOrNull { containsToken(n, it) }
    }

    private fun densityToken(entry: String): String? {
        if (!isConfigSplit(entry)) return null
        val n = entry.lowercase().replace('-', '_')
        return DENSITY_DPI.keys.firstOrNull { containsToken(n, it) }
    }

    // A config split names its dimension in the file name across all three formats:
    // "config.arm64_v8a.apk" (xapk), "split_config.arm64_v8a.apk" (apkm),
    // "base-arm64_v8a.apk" (apks). Plain base/feature APKs don't match this.
    private fun isConfigSplit(entry: String): Boolean {
        val n = entry.substringAfterLast('/').lowercase()
        return n.contains("config") || n.contains("split_") || Regex("base[-_]").containsMatchIn(n)
    }

    // True if [token] appears in [text] bounded by non-alphanumeric characters, so
    // "x86" doesn't match inside "x86_64".
    private fun containsToken(text: String, token: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(token, from)
            if (i < 0) return false
            val before = if (i == 0) ' ' else text[i - 1]
            val afterIndex = i + token.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = i + 1
        }
    }
}
