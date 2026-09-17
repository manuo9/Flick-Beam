package com.flickbeam.shared

/**
 * Numeric error codes shared by the HTTP endpoints and the control channel.
 * On HTTP these are status codes; on the WebSocket the same numbers are carried
 * inside an [ErrorMessage]. See docs/FlickBeam_Protocol.md.
 */
object ErrorCodes {
    const val BAD_REQUEST = 400
    const val INVALID_TOKEN = 401
    const val PAIRING_REQUIRED = 403
    const val NOT_FOUND = 404
    const val PAYLOAD_TOO_LARGE = 413
    const val UPDATE_REQUIRED = 426
    const val INSUFFICIENT_STORAGE = 507
}
