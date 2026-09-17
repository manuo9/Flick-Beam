package com.flickbeam.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every message sent over the WebSocket control channel. Serialized as JSON
 * with a "type" field, for example {"type":"DPAD_UP"} or
 * {"type":"TEXT","value":"hello"}. Use [FlickBeamJson] to encode and decode.
 *
 * Grouped to match the message catalogue in docs/FlickBeam_Protocol.md:
 * handshake, pairing, remote, media, heartbeat.
 */
@Serializable
sealed class ControlMessage

// --- Handshake ---

@Serializable
@SerialName("HELLO")
data class Hello(
    val deviceId: String,
    val name: String,
    val model: String? = null,
    val token: String? = null,
    val capabilities: List<Capability> = emptyList(),
    val protocol: Int = PROTOCOL_VERSION,
    val app: String = APP_ID,
) : ControlMessage()

@Serializable
@SerialName("HELLO_RESPONSE")
data class HelloResponse(
    val device: String,
    val deviceId: String,
    val version: String,
    val capabilities: List<Capability> = emptyList(),
    val protocol: Int = PROTOCOL_VERSION,
) : ControlMessage()

@Serializable
@SerialName("ERROR")
data class ErrorMessage(
    val code: Int,
    val reason: String,
) : ControlMessage()

// --- Pairing ---

@Serializable
@SerialName("PAIR_REQUIRED")
data object PairRequired : ControlMessage()

@Serializable
@SerialName("PAIR")
data class PairRequest(
    val deviceId: String,
    val code: String,
) : ControlMessage()

@Serializable
@SerialName("PAIR_SUCCESS")
data class PairSuccess(
    val token: String,
    val tvDeviceId: String,
    val name: String,
) : ControlMessage()

/** Sent by the TV right before it closes the socket after a deliberate Disconnect. */
@Serializable
@SerialName("DISCONNECTED")
data object Disconnected : ControlMessage()

// --- Remote ---

@Serializable
@SerialName("DPAD_UP")
data object DpadUp : ControlMessage()

@Serializable
@SerialName("DPAD_DOWN")
data object DpadDown : ControlMessage()

@Serializable
@SerialName("DPAD_LEFT")
data object DpadLeft : ControlMessage()

@Serializable
@SerialName("DPAD_RIGHT")
data object DpadRight : ControlMessage()

@Serializable
@SerialName("OK")
data object Ok : ControlMessage()

@Serializable
@SerialName("BACK")
data object Back : ControlMessage()

@Serializable
@SerialName("MENU")
data object Menu : ControlMessage()

@Serializable
@SerialName("TEXT")
data class Text(
    val value: String,
) : ControlMessage()

// --- Media (cast/play from phone) ---

/** Tells the TV to play a video or audio file streamed from the phone at [url]. */
@Serializable
@SerialName("PLAY_MEDIA")
data class PlayMedia(
    val url: String,
    val title: String,
    val mimeType: String? = null,
) : ControlMessage()

/** Tells the TV to show a photo slideshow of images streamed from the phone. */
@Serializable
@SerialName("PLAY_SLIDESHOW")
data class PlaySlideshow(
    val urls: List<String>,
) : ControlMessage()

// --- Heartbeat ---

@Serializable
@SerialName("PING")
data object Ping : ControlMessage()

@Serializable
@SerialName("PONG")
data object Pong : ControlMessage()
