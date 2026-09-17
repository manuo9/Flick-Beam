package com.flickbeam.remote.network

import android.net.Uri

/** One file in a send batch, plus its current status. */
data class SendItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val status: SendItemStatus,
)

/** Where a single file is in its journey to the TV. */
sealed interface SendItemStatus {
    /** Queued, not started yet. Only these can be cancelled with the ✕. */
    data object Waiting : SendItemStatus

    /** Uploading now. [percent] is 0..100, or -1 if the size is unknown. */
    data class Uploading(val percent: Int) : SendItemStatus

    /** Arrived on the TV. */
    data object Sent : SendItemStatus

    /** Upload failed (e.g. the network dropped). Can be retried. */
    data class Failed(val message: String) : SendItemStatus

    /** Pulled out of the queue by the user before it started. */
    data object Canceled : SendItemStatus
}
