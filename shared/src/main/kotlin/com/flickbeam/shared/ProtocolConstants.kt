package com.flickbeam.shared

import kotlinx.serialization.json.Json

/** Identifies the FlickBeam app in the handshake. */
const val APP_ID: String = "FlickBeam"

/** NSD service type the TV registers and the phone browses for. */
const val NSD_SERVICE_TYPE: String = "_flickbeam._tcp."

/** HTTP header carrying the pairing token on data-channel requests. */
const val TOKEN_HEADER: String = "X-FlickBeam-Token"

/** HTTP header carrying the (URL-encoded) file name on an upload request. */
const val UPLOAD_NAME_HEADER: String = "X-FlickBeam-Name"

/** Endpoint paths on the receiving side's server. */
object Endpoints {
    const val CONTROL = "/control"

    // Real requests target this with a transfer id, e.g. /upload/{transferId}.
    const val UPLOAD = "/upload"
}

/** Keys used in the NSD TXT record so the phone can list TVs before connecting. */
object TxtKeys {
    const val DEVICE_ID = "deviceId"
    const val NAME = "name"
    const val PROTOCOL = "protocol"
}

/**
 * The single JSON configuration both apps use for control-channel messages.
 * The class discriminator "type" matches the message catalogue in the spec,
 * e.g. {"type":"DPAD_UP"}.
 */
val FlickBeamJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
