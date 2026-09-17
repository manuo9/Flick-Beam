package com.flickbeam.tv.network

import com.flickbeam.shared.Back
import com.flickbeam.shared.Capability
import com.flickbeam.shared.ControlMessage
import com.flickbeam.shared.Disconnected
import com.flickbeam.shared.DpadDown
import com.flickbeam.shared.DpadLeft
import com.flickbeam.shared.DpadRight
import com.flickbeam.shared.DpadUp
import com.flickbeam.shared.Endpoints
import com.flickbeam.shared.ErrorCodes
import com.flickbeam.shared.ErrorMessage
import com.flickbeam.shared.Hello
import com.flickbeam.shared.HelloResponse
import com.flickbeam.shared.Menu
import com.flickbeam.shared.Ok
import com.flickbeam.shared.PlayMedia
import com.flickbeam.shared.PlaySlideshow
import com.flickbeam.shared.PairRequest
import com.flickbeam.shared.PairRequired
import com.flickbeam.shared.PairSuccess
import com.flickbeam.shared.Ping
import com.flickbeam.shared.Pong
import com.flickbeam.shared.Text
import com.flickbeam.shared.FlickBeamJson
import com.flickbeam.shared.TOKEN_HEADER
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import com.flickbeam.shared.UPLOAD_NAME_HEADER
import fi.iki.elonen.NanoWSD
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.URLDecoder

/**
 * The TV's embedded server. NanoWSD hosts the /control WebSocket for the FlickBeam
 * protocol; regular HTTP handles the /upload data endpoint.
 *
 * Bind to port 0 so the OS picks a free (ephemeral) port; read [listeningPort]
 * after starting to learn which one, then advertise it over NSD.
 */
class FlickBeamServer(
    private val identity: DeviceIdentityStore,
    private val files: IncomingFiles,
    private val authority: PairingAuthority,
    private val events: Events,
) : NanoWSD(0) {

    /** Reports control-channel and transfer activity so the UI can react. */
    interface Events {
        fun onClientConnected(name: String)
        fun onClientDisconnected()
        fun onRemote(message: ControlMessage)
        fun onUploadStarted(name: String)
        fun onUploadProgress(percent: Int)
        fun onUploadFinished(name: String)
        fun onUploadFailed(name: String)
    }

    // The single currently-authenticated control socket, if any. Only one phone
    // may be connected at a time.
    @Volatile
    private var activeSocket: ControlSocket? = null

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = ControlSocket(handshake)

    /** Tell the connected phone it's being disconnected, then close the socket. */
    fun disconnectActiveClient() {
        activeSocket?.let { socket ->
            runCatching { socket.send(FlickBeamJson.encodeToString<ControlMessage>(Disconnected)) }
            runCatching { socket.close(WebSocketFrame.CloseCode.NormalClosure, "disconnected", false) }
        }
        activeSocket = null
    }

    override fun serveHttp(session: IHTTPSession): Response {
        val uri = session.uri ?: ""
        return when {
            session.method == NanoHTTPD.Method.PUT && uri.startsWith(Endpoints.UPLOAD + "/") ->
                handleUpload(session)

            else ->
                NanoHTTPD.newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Not found",
                )
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        // Only paired phones may upload.
        val token = session.headers[TOKEN_HEADER.lowercase()]
        if (!authority.isPaired(token)) {
            return NanoHTTPD.newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                NanoHTTPD.MIME_PLAINTEXT,
                "unauthorized",
            )
        }

        val name = session.headers[UPLOAD_NAME_HEADER.lowercase()]
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?: "received_file"
        val length = session.headers["content-length"]?.toLongOrNull() ?: -1L

        events.onUploadStarted(name)
        return try {
            val savedName = files.save(name, session.inputStream, length) { percent ->
                events.onUploadProgress(percent)
            }
            events.onUploadFinished(savedName)
            NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, savedName)
        } catch (e: Exception) {
            events.onUploadFailed(name)
            NanoHTTPD.newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                NanoHTTPD.MIME_PLAINTEXT,
                "failed",
            )
        }
    }

    private inner class ControlSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        // Only a paired phone is allowed to send remote/transfer commands.
        private var authenticated = false
        private var clientName = "phone"

        override fun onOpen() {}

        override fun onClose(
            code: WebSocketFrame.CloseCode?,
            reason: String?,
            initiatedByRemote: Boolean,
        ) {
            // Only report a disconnect if this is still the live socket. A socket that
            // was superseded by a reconnect must not clear the new one's state.
            if (authenticated && activeSocket === this) {
                activeSocket = null
                events.onClientDisconnected()
            }
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) {}

        override fun onMessage(message: WebSocketFrame) {
            val incoming = runCatching {
                FlickBeamJson.decodeFromString<ControlMessage>(message.textPayload)
            }.getOrNull() ?: return

            when (incoming) {
                is Hello -> {
                    clientName = incoming.name
                    if (!authority.isPaired(incoming.token)) {
                        // Pairing is only ever started from the TV; a phone with
                        // no/invalid token just waits until the TV owner pairs it.
                        reply(PairRequired)
                    } else {
                        // A valid token is always the one paired phone (single-device
                        // pairing), so a reconnect supersedes any older/stale socket
                        // instead of being turned away as busy.
                        val previous = activeSocket
                        activeSocket = this
                        authenticated = true
                        if (previous != null && previous !== this) {
                            runCatching {
                                previous.close(
                                    WebSocketFrame.CloseCode.NormalClosure,
                                    "superseded",
                                    false,
                                )
                            }
                        }
                        reply(handshakeResponse())
                        events.onClientConnected(clientName)
                    }
                }

                is PairRequest -> {
                    val token = authority.completePairing(incoming.deviceId, clientName, incoming.code)
                    if (token != null) {
                        authenticated = true
                        activeSocket = this
                        reply(
                            PairSuccess(
                                token = token,
                                tvDeviceId = identity.deviceId,
                                name = identity.deviceName,
                            ),
                        )
                        events.onClientConnected(clientName)
                    } else {
                        reply(ErrorMessage(ErrorCodes.PAIRING_REQUIRED, "pairing_failed"))
                    }
                }

                is Ping -> reply(Pong)

                is DpadUp, is DpadDown, is DpadLeft, is DpadRight, is Ok, is Back, is Menu,
                is Text, is PlayMedia, is PlaySlideshow ->
                    if (authenticated) events.onRemote(incoming)

                else -> Unit
            }
        }

        private fun reply(message: ControlMessage) {
            runCatching { send(FlickBeamJson.encodeToString<ControlMessage>(message)) }
        }
    }

    private fun handshakeResponse(): ControlMessage = HelloResponse(
        device = identity.deviceName,
        deviceId = identity.deviceId,
        version = identity.appVersion,
        capabilities = listOf(
            Capability.REMOTE,
            Capability.KEYBOARD,
            Capability.SEND,
            Capability.RECEIVE,
        ),
    )
}
