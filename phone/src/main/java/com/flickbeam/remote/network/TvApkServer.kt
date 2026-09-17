package com.flickbeam.remote.network

import android.content.Context
import fi.iki.elonen.NanoHTTPD

/** Filename the bundled TV apk is copied to at build time (see phone/build.gradle.kts). */
const val TV_APK_ASSET_NAME = "flickbeam-tv.apk"

/**
 * Serves the TV apk bundled in this app's assets, so a browser or a sideload app
 * (e.g. Downloader) on a TV with no FlickBeam installed yet can fetch and install it.
 * Responds with the apk on any path, so the address to type on the TV is as short
 * as the phone's ip:port.
 */
class TvApkServer(
    private val context: Context,
) : NanoHTTPD(0) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            val assetFd = context.assets.openFd(TV_APK_ASSET_NAME)
            newFixedLengthResponse(
                Response.Status.OK,
                "application/vnd.android.package-archive",
                assetFd.createInputStream(),
                assetFd.length,
            ).apply {
                addHeader("Content-Disposition", "attachment; filename=\"$TV_APK_ASSET_NAME\"")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "TV apk not bundled with this build",
            )
        }
    }
}
