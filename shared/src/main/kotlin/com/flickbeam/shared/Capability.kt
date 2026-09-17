package com.flickbeam.shared

import kotlinx.serialization.Serializable

/**
 * Things a device can do over the FlickBeam protocol. Exchanged in the handshake
 * so each side can adapt to what the other supports.
 *
 * A device must never advertise a capability it does not actually implement.
 * Future values are listed so newer peers that advertise them can still be
 * parsed by older builds. See docs/FlickBeam_Protocol.md.
 */
@Serializable
enum class Capability {
    // Implemented in Protocol 1.
    REMOTE,
    KEYBOARD,
    SEND,
    RECEIVE,

    // Reserved for the future. Not advertised until implemented.
    BROWSE,
    CLIPBOARD,
    SEARCH,
    INSTALL_APK,
    MEDIA,
    NOTIFY,
}
