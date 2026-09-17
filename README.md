# FlickBeam

<img src="public/logo.png" alt="FlickBeam logo" width="160" />

**FlickBeam** connects your Android phone and Android TV over your local Wi-Fi — send files, cast photos/video/audio, browse TV storage, install APKs, and use your phone as a remote with keyboard input.

**No internet. No cloud. No account. Everything stays on your local network.**

## Apps

FlickBeam consists of two Android apps sharing one local-network protocol:

- **FlickBeam TV** (`tv/`) — the Android TV app. Browse and manage TV storage, play media, receive files, install APKs, and receive remote commands from the phone.
- **FlickBeam Remote** (`phone/`) — the Android phone companion. Discover and pair with the TV, send and cast files, and use your phone as a remote and keyboard.
- **shared** (`shared/`) — pure Kotlin module containing the protocol, models, serialization, and shared constants.

## Getting FlickBeam onto your TV

The phone app can include the TV APK, so you can install FlickBeam TV without downloading it from the internet.

1. Install **FlickBeam Remote** on your Android phone.
2. Connect the phone and Android TV to the same Wi-Fi network.
3. Open **Install TV App** in FlickBeam Remote.
4. The phone serves the TV APK over the local network.
5. Open the provided address in the TV's browser.
6. Download the TV APK from the phone.
7. Install FlickBeam TV on the TV.
8. Open FlickBeam TV and pair your phone using the 6-digit code shown on the TV.

You can also download the standalone TV APK directly from **GitHub Releases** if you already have a way to sideload APKs.

## Features

### 📺 On the TV

- **File Manager** — browse, copy, move, rename, delete, and manage local files.
- **APK Installer** — install `.apk`, `.apks`, `.apkm`, and `.xapk` packages.
- **Video & Audio Player** — Media3/ExoPlayer playback with D-pad controls and same-folder subtitle loading.
- **Photo Viewer** — view photos and run slideshows.
- **Receive Files** — receive files directly from the phone.
- **Live Casting** — play video, audio, and photo slideshows from the phone over the local network.
- **Remote Control** — receive D-pad, OK, Back, Menu, and text input commands from the phone.

### 📱 On the Phone

- **TV Discovery** — automatically discover FlickBeam TVs on the local network using NSD/mDNS.
- **Manual Connection** — connect using the TV's IP address when discovery is unavailable.
- **Pairing** — pair using a 6-digit code displayed on the TV and reuse the saved pairing for later connections.
- **File Transfer** — send files to the TV with per-file progress, cancellation of waiting files, and retry support.
- **Casting** — cast photos, video, and audio to the TV over the local network.
- **Remote Control** — control FlickBeam TV using D-pad, OK, Back, and Menu controls.
- **Keyboard** — type on your phone and send text input directly to the TV.
- **TV App Installer** — serve the TV APK directly from the phone so FlickBeam TV can be installed without internet access.

The phone remote controls FlickBeam itself, not other TV apps or the entire TV system.

## How It Works

FlickBeam works entirely over the local network.

The phone discovers the TV using **NSD/mDNS** and establishes a persistent WebSocket connection for:

- Pairing
- Authentication
- Remote commands
- Keyboard input
- Casting control
- Connection heartbeats

File transfers use HTTP for streaming file data.

During initial setup, the phone can also temporarily act as an HTTP server and provide the TV APK to a browser on the TV.

No cloud service, online account, or external server is required.

See [`docs/FlickBeam_Protocol.md`](docs/FlickBeam_Protocol.md) for the protocol specification.

## Security

FlickBeam is designed for use on a trusted local network.

Pairing uses a short-lived 6-digit code displayed on the TV. After successful pairing, the devices exchange a persistent authentication token used for subsequent connections.

FlickBeam does not require an online account or cloud backend. Local HTTP and WebSocket traffic is not encrypted; pairing authenticates the phone but does not provide transport encryption.

## Tech Stack

- Kotlin
- Jetpack Compose
- Compose for TV
- Material 3
- Hilt
- Coroutines / StateFlow
- Media3 / ExoPlayer
- Coil
- NanoHTTPD / NanoWSD
- OkHttp
- kotlinx.serialization

## Building

Open the repository root in Android Studio and select the `tv` or `phone` configuration.

### Build the TV debug APK

```bash
./gradlew :tv:assembleDebug
```

Output: `tv/build/outputs/apk/debug/tv-debug.apk`

### Build the phone debug APK

```bash
./gradlew :phone:assembleDebug
```

Output: `phone/build/outputs/apk/debug/phone-debug.apk`

### Build both apps

```bash
./gradlew :tv:assembleDebug :phone:assembleDebug
```

### Bundle the TV APK inside the phone app

For the **Install TV App** feature, refresh the bundled TV APK:

```bash
./gradlew :phone:copyTvApk
```

This task builds the TV debug APK and copies it into the phone assets. Then build the phone APK to include the updated asset:

```bash
./gradlew :phone:assembleDebug
```

Ordinary phone builds do not refresh the bundled TV APK. Repeat these two steps when distributing TV changes through the phone installer.

Use **JDK 17**, Android SDK Platform 35, and Build Tools 35.0.0. Set the SDK location through Android Studio or `local.properties`.

On Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`.

## Project Structure

```text
FlickBeam/
|-- tv/                 Android TV app
|-- phone/              Android phone companion
|-- shared/             Pure Kotlin protocol module
|-- docs/               Protocol specification and documentation
|-- public/             Logo and public assets
|-- build.gradle.kts
|-- settings.gradle.kts
`-- README.md
```

## Downloads

Check [GitHub Releases](../../releases) for published APKs:

- **TV APK** — install on your Android TV.
- **Remote APK** — install on your Android phone.

The Remote APK can also contain the TV APK and help install FlickBeam on a TV over the local network.

## Requirements

- Android TV
- Android phone or tablet
- Both devices connected to the same local Wi-Fi network
- Minimum SDK: 23 (Android 6.0)
- Compile SDK: 35
- Target SDK: 34

## License

MIT — see [LICENSE](LICENSE).
