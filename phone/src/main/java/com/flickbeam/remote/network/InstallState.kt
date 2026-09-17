package com.flickbeam.remote.network

/** What the "Install TV app" feature is currently doing, for the UI to show. */
sealed interface InstallState {
    /** Not serving. */
    data object Stopped : InstallState

    /** Binding the server. */
    data object Starting : InstallState

    /** Ready: the TV apk can be fetched from this phone at host:port. */
    data class Listening(val host: String?, val port: Int) : InstallState

    /** Something went wrong while starting. */
    data class Error(val message: String) : InstallState
}
