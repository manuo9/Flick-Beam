package com.flickbeam.tv.ui.home

/** One tile on the Home screen. */
data class FeatureTile(
    val id: String,
    val title: String,
    val description: String,
)

val homeFeatureTiles = listOf(
    FeatureTile(
        id = "file_manager",
        title = "File Manager",
        description = "Browse and manage files on this device",
    ),
    FeatureTile(
        id = "receive",
        title = "Receive",
        description = "Receive files from your phone or computer",
    ),
    FeatureTile(
        id = "more_apps",
        title = "More by manuo9",
        description = "Other apps",
    ),
)
