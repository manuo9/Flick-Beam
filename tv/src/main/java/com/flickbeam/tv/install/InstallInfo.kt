package com.flickbeam.tv.install

/** What the inspect screen shows about a package before installing it. */
data class InstallInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val minSdk: Int?,
    val targetSdk: Int?,
    val architecture: String,
    val splitCount: Int,
    val permissions: List<String>,
)
