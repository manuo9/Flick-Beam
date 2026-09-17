package com.flickbeam.shared

/**
 * Current FlickBeam wire protocol version.
 *
 * Bumped only on breaking changes. Additive features are negotiated through
 * capabilities, not by changing this number. See docs/FlickBeam_Protocol.md.
 */
const val PROTOCOL_VERSION: Int = 1
