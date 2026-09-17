package com.flickbeam.tv.ui.navigation

/** The main screens you can be on. Simple, since it's just Home <-> a feature. */
sealed class Screen {
    data object Home : Screen()
    data object FileManager : Screen()
    data object Receive : Screen()
    data object MoreApps : Screen()
}
