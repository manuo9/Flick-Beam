# FlickBeam Protocol

Version: Protocol 1

This document describes how the FlickBeam TV app and the FlickBeam Remote phone
companion app talk to each other over the local WiFi network. It is the shared
contract both apps are built against. It is a specification only, no code.

The design goal is one reusable protocol, not a one-off file-transfer hack.
Remote control, keyboard, and file transfer all share a single connection
lifecycle. New features (browse, clipboard, search, install APK, media,
notifications) are added later as new message types and capabilities, without
breaking older peers.

---

## 1. Overview and Architecture

Two apps on the same local network:

- **TV app** runs on the Android TV.
- **Phone app** runs on an Android phone. It also hosts the Remote feature,
  which does not exist on the TV.

The phone finds the TV over the network, opens a persistent control link, and
uses a separate link for bulk file bytes.

```
        Phone
          |
          |  1. NSD discovery  (_flickbeam._tcp)
          v
        TV  (advertises name + deviceId + port)
          |
          |  2. WebSocket  /control     (persistent, tiny messages)
          |     handshake, pairing, remote events, cast messages
          |
          |  3. HTTP  /upload  (on demand, large streaming bytes)
          v
     file lands on the TV
```

**One protocol, one connection lifecycle, two transports:**

- **Control channel = WebSocket (`/control`).** Persistent. Carries the
  handshake, capability exchange, pairing, remote and keyboard events, and
  cast messages (play a photo/video/audio file streamed from the phone).
  Messages are small JSON objects.
- **Data channel = HTTP (`/upload`).** On demand. Carries the actual file
  bytes, streamed. File bytes never travel over the WebSocket.

The TV hosts the control server and the file receiver. The phone additionally
runs small local HTTP servers of its own when acting as a source: one to serve
its installer APK to the TV, and one to stream media files to the TV for
casting.

| Flow                     | Server (listens)        | Client (connects) |
|--------------------------|-------------------------|-------------------|
| Remote control           | TV (`/control`)         | Phone             |
| File: phone -> TV        | TV (`/upload`)          | Phone             |
| Cast media: phone -> TV  | Phone (temp HTTP)       | TV                |

---

## 2. Device Identity

Every device has a stable unique id (a UUID) generated once on first launch and
kept for the life of the install. This is the identity. The display name is
just a label and may change at any time.

- `deviceId` — stable UUID, e.g. `550e8400-e29b-41d4-a716-446655440000`.
- `name` — human label, e.g. "Living Room TV" or "Manu's Pixel 9".

Pairing and paired-device records are keyed on `deviceId`, never on the name.
If a TV is renamed from "Living Room" to "Bedroom", the pairing still works,
because identity is the `deviceId`.

Both the TV and the phone have their own `deviceId`. When a phone re-pairs, the
TV matches on the phone `deviceId` and updates the existing record instead of
creating a duplicate.

---

## 3. Discovery

### Primary: NSD (Network Service Discovery / mDNS)

- Service type: `_flickbeam._tcp.`
- The **TV registers** the service. The **phone browses** for it.
- The TV binds to a **dynamic (ephemeral) port** and advertises it. The port is
  not hardcoded, which avoids collisions.
- Service record contents:
  - **SRV**: host and port.
  - **TXT**: `deviceId`, `name`, `protocol` (integer).

The phone shows discovered TVs as a simple list the user picks from:

```
Devices
  Living Room TV
  Bedroom TV
```

### Fallback: QR

If discovery fails (guest WiFi, routers that block mDNS), the TV can show a QR
code that encodes its address, `host:port`. The phone scans it.

### Fallback: Manual IP

As a last resort the phone lets the user type the TV IP. The port is shown on
the TV or defaults to the advertised value.

An "address", however it is obtained (NSD, QR, or manual), is always the pair
`host:port`. The rest of the protocol is identical regardless of how the phone
found the TV.

---

## 4. Pairing

Pairing happens once per phone/TV pair. The phone identifies itself first, so
the TV knows exactly which phone is asking (this cleanly handles two phones
trying to pair at the same time, each gets its own pairing session and code).

```
Phone  -> HELLO(deviceId, token = null)
TV     -> PAIR_REQUIRED
          and shows a 6-digit code on the TV screen, e.g. 483271
          (the pairing session is bound to that phone deviceId)
Phone     user types the code on the phone (fast phone keyboard)
Phone  -> PAIR(deviceId, code)
TV     -> validates the code
TV     -> PAIR_SUCCESS(token, tvDeviceId, name)
Phone     stores { tvDeviceId, token }
          Connected
```

The pairing code is:

- **Single-use** — valid for one successful pairing only.
- **Time-limited** — it expires after a short window (about 60 seconds). After
  expiry the TV generates a new one.
- **Device-bound** — tied to the requesting phone `deviceId` for that session.

The 6-digit code is shown on the TV (easy to read) and typed on the phone
(faster than a TV on-screen keyboard).

Once paired, the phone reconnects automatically using its stored token and does
not need to pair again.

---

## 5. Authentication and Paired-Device Storage

Each paired phone gets one token, issued by the TV during pairing.

A valid token is required on:

- the WebSocket control channel,
- upload,
- any future API.

The token is carried as a header on HTTP requests and included in the handshake
on the control channel.

### Paired-device records

The TV pairs with a single phone at a time: pairing a new phone replaces the
existing record and invalidates its token. The phone, on the other hand, may
hold a stored token per TV it has paired with (it can be used with several
TVs, one at a time).

**TV stores** (one record — the currently paired phone, if any):

| Field         | Example                    |
|---------------|----------------------------|
| deviceId      | 9c87b3...                  |
| name          | Manu's Pixel 9             |
| model         | Pixel 9                    |
| token         | (opaque secret)            |
| firstPaired   | 2026-07-30T06:40:00Z       |
| lastConnected | 2026-07-30T07:10:00Z       |

**Phone stores** (one record per known TV):

| Field         | Example                    |
|---------------|----------------------------|
| tvDeviceId    | 550e8400...                |
| name          | Living Room TV             |
| token         | (opaque secret)            |
| lastConnected | 2026-07-30T07:10:00Z       |

Removing a device deletes its token, which forces a fresh pairing next time.

---

## 6. Protocol Version

The current protocol version is **1**.

- The version is a single integer.
- It is incremented **only on breaking changes**.
- Additive features do not bump the version. They are negotiated through
  capabilities (section 7) instead.

Each side sends its protocol version in the handshake. If the versions are
incompatible, the newer side shows a plain "update the other app" message
rather than trying to half-work. See error code 426 in section 10.

---

## 7. Capabilities

Devices do not assume features exist. Each side advertises what it supports, so
the other side adapts its UI automatically. A device never advertises a feature
it does not actually implement.

Capabilities are exchanged **both directions**, so each side can adapt
independently. The phone advertises its set in `HELLO`; the TV advertises its
set in `HELLO_RESPONSE`.

**Current capabilities (Protocol 1):**

- `REMOTE`
- `KEYBOARD`
- `SEND`
- `RECEIVE`

**Reserved for the future (not advertised until implemented):**

- `BROWSE`
- `CLIPBOARD`
- `SEARCH`
- `INSTALL_APK`
- `MEDIA`
- `NOTIFY`

---

## 8. Message Catalogue

All control-channel messages are JSON objects with a `type` field. Messages are
grouped by category below. Fields shown are the meaningful ones; every message
may also carry protocol housekeeping fields.

### Handshake

```
HELLO
  { "type": "HELLO", "protocol": 1, "app": "FlickBeam",
    "deviceId": "...", "name": "Manu's Pixel 9", "model": "Pixel 9",
    "token": "<stored token or null>",
    "capabilities": ["SEND", "RECEIVE"] }

HELLO_RESPONSE
  { "type": "HELLO_RESPONSE", "protocol": 1,
    "device": "Living Room TV", "deviceId": "...", "version": "1.0",
    "capabilities": ["REMOTE", "KEYBOARD", "SEND", "RECEIVE"] }

ERROR
  { "type": "ERROR", "code": 403, "reason": "pairing_required" }
```

### Pairing

```
PAIR_REQUIRED
  { "type": "PAIR_REQUIRED" }          // TV is now showing a code on screen

PAIR
  { "type": "PAIR", "deviceId": "...", "code": "483271" }

PAIR_SUCCESS
  { "type": "PAIR_SUCCESS", "token": "...", "tvDeviceId": "...",
    "name": "Living Room TV" }
```

A wrong or expired code is answered with an `ERROR` (see section 10).

### Remote

```
DPAD_UP     { "type": "DPAD_UP" }
DPAD_DOWN   { "type": "DPAD_DOWN" }
DPAD_LEFT   { "type": "DPAD_LEFT" }
DPAD_RIGHT  { "type": "DPAD_RIGHT" }
OK          { "type": "OK" }
BACK        { "type": "BACK" }
TEXT        { "type": "TEXT", "value": "hello" }
```

### Media (cast/play from phone)

Tells the TV to open its player on a file the phone is streaming over HTTP
from its own temporary server. No offer/accept negotiation — the phone starts
its server, then sends the message; the TV just requests the URL.

```
PLAY_MEDIA
  { "type": "PLAY_MEDIA", "url": "http://192.168.1.20:8080/video.mp4",
    "title": "movie.mkv", "mimeType": "video/mp4" }

PLAY_SLIDESHOW
  { "type": "PLAY_SLIDESHOW",
    "urls": ["http://192.168.1.20:8080/1.jpg", "http://192.168.1.20:8080/2.jpg"] }
```

### Heartbeat

```
PING   { "type": "PING" }
PONG   { "type": "PONG" }
```

Used to detect a dead connection (device asleep, WiFi dropped). If a peer stops
answering, the connection is treated as lost. The phone can then reconnect
automatically: re-resolve the TV over NSD and re-run the handshake with its
stored token. No re-pairing is needed.

---

## 9. Endpoints

```
/control          WebSocket. The control channel. Requires a valid token in
                  the handshake. Carries all messages in section 8.

/upload/{id}      HTTP PUT, on the TV. Streams file bytes from the phone; {id}
                  is an arbitrary unique path segment the TV ignores. The real
                  file name travels in the X-FlickBeam-Name header. Requires a
                  valid token header.
```

The phone's own temporary servers (installer APK, cast media) are not part of
this fixed endpoint set — they're started on demand on an ephemeral port and
their URL is passed to the TV directly in the relevant message.

Uploads stream bytes and do not buffer whole files in memory.

---

## 10. Error Codes

The same numeric code set is used everywhere. On HTTP endpoints these are HTTP
status codes. On the WebSocket control channel the same numbers are returned
inside an `ERROR` message, because there are no HTTP statuses after the upgrade.

| Code | Meaning              | When                                          |
|------|----------------------|-----------------------------------------------|
| 400  | Bad Request          | Malformed message or offer                    |
| 401  | Invalid Token        | Missing or wrong token                        |
| 403  | Pairing Required     | No token yet, must pair first                 |
| 404  | Not Found            | Unknown endpoint                              |
| 413  | Payload Too Large    | Offer exceeds an allowed size                 |
| 426  | Update Required      | Incompatible protocol version                 |
| 507  | Insufficient Storage | Receiver is out of disk space                 |

Example on the control channel:

```
{ "type": "ERROR", "code": 401, "reason": "invalid_token" }
```

---

## 11. Connection Lifecycle

```
1. Discover     phone finds the TV (NSD, or QR / manual IP fallback)
2. Connect      phone opens the /control WebSocket
3. Handshake    HELLO / HELLO_RESPONSE, protocol + capabilities
4. Authorize    token checked; if none, pair first (section 4)
5. Active       remote events and/or transfers flow; PING/PONG keeps it alive
6. Reconnect    on a dropped link, phone re-resolves and re-handshakes with the
                stored token (no re-pairing)
```

The TV pairs with exactly one phone at a time (section 5 keeps a single
record, not a list). Only one control connection is live at once: if a
`HELLO` arrives with a valid token while another socket is still open — the
same phone reconnecting after a drop, most commonly — the new socket
**supersedes** the old one immediately (the TV sends it `DISCONNECTED` and
closes it) rather than being refused. There is no busy/rejection case.

---

## 12. Security Considerations

- The protocol is designed for a **local, trusted WiFi network** (a home LAN).
- Traffic is **plaintext** HTTP and WebSocket. A determined observer on the same
  network could capture a token. The 6-digit code prevents casual unauthorized
  pairing, and the token prevents re-entering the code, which is a reasonable
  bar for a home LAN tool.
- The pairing code is single-use, time-limited, and bound to the requesting
  device, which limits the window for guessing it.
- Transport encryption (TLS) is a possible future improvement. It is not part of
  Protocol 1 to avoid the self-signed certificate friction on Android.

---

## 13. Versioning and Changelog

- **Protocol 1** — initial specification. Discovery (NSD with QR and manual IP
  fallback), pairing with a device-bound 6-digit code and a single-slot token,
  WebSocket control channel with single-active-connection supersede, HTTP
  upload data channel, remote and keyboard events, cast (play-from-phone)
  messages, capabilities, shared error code set, heartbeat.

Future breaking changes bump the protocol integer. Additive features arrive
through new capabilities and new message types and do not require a version bump.
