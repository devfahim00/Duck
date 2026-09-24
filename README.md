# Duck

[![CI](https://github.com/devfahim00/Duck/actions/workflows/android.yml/badge.svg)](https://github.com/devfahim00/Duck/actions/workflows/android.yml)

Duck is a video downloader for Android powered by **yt-dlp**.

## Features

- Paste a link from YouTube or any of the 1000+ sites supported by yt-dlp
- Quality selection: Best / 4K / 1080p / 720p / lower / audio-only
- Video + audio streams are merged automatically with the bundled FFmpeg
- Built-in download manager with queue, live progress, speed and ETA, cancel / retry, and history
- Multi-threaded downloading for extra speed:
  - concurrent fragment downloads (yt-dlp `-N`), up to 32 threads
  - optional Turbo mode using the bundled aria2c multi-connection downloader
- Multiple simultaneous downloads
- Share links from any app straight into Duck
- Update the yt-dlp engine to the latest release from within the app
- Auto app update checks against GitHub Releases on every launch, plus a manual check in Settings
- Telegram support from the owner: [@droxilen](https://t.me/droxilen)

## v1.0.0

- **Automatic app updates** — silent GitHub Releases check on launch with a
  one-tap APK download dialog
- **Manual update check** in Settings → *App updates*
- **Up to 32 download threads** (4 / 8 / 16 / 32)
- **Telegram** — news and support from the owner at
  [@droxilen](https://t.me/droxilen)
- First tagged release: CI builds a signed release APK and publishes it to
  [Releases](https://github.com/devfahim00/Duck/releases) with the full
  changelog on every `v*` tag

## Tech

Kotlin, Jetpack Compose (Material 3), [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (bundles yt-dlp, Python, FFmpeg and aria2c). Typeface: [Inter](https://rsms.me/inter) (SIL OFL 1.0).

## Building

Every push is built by GitHub Actions. Tagged `v*` pushes publish a signed
release APK to [Releases](https://github.com/devfahim00/Duck/releases);
branch pushes upload the APK as a workflow artifact.

Locally: `./gradlew assembleDebug` (JDK 17, Android SDK 35).
