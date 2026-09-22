# Duck

[![CI](https://github.com/devfahim00/Duck/actions/workflows/android.yml/badge.svg)](https://github.com/devfahim00/Duck/actions/workflows/android.yml)

Duck is a video downloader for Android powered by **yt-dlp**.

## Features

- Paste a link from YouTube or any of the 1000+ sites supported by yt-dlp
- Quality selection: Best / 4K / 1080p / 720p / lower / audio-only
- Video + audio streams are merged automatically with the bundled FFmpeg
- Built-in download manager with queue, live progress, speed and ETA, cancel / retry, and history
- Multi-threaded downloading for extra speed:
  - concurrent fragment downloads (yt-dlp `-N`)
  - optional Turbo mode using the bundled aria2c multi-connection downloader
- Multiple simultaneous downloads
- Share links from any app straight into Duck
- Update the yt-dlp engine to the latest release from within the app

## v1.1.0

- **Fixed live progress**: downloads now show percentage, speed and ETA in
  real time instead of sitting at 0% until completion (yt-dlp's `--print`
  implied quiet mode, which silently disabled progress output; the download
  command now passes `--progress` and every stdout line is parsed directly)
- **Ultra-modern UI**: dark "midnight glass" redesign with Inter typography,
  gradient progress bars with shimmer, floating pill navigation, animated
  tab transitions, quality badges and a restyled settings sheet
- **arm64-v8a only** builds - smaller APK (~70 MB), built by CI on every push

## Tech

Kotlin, Jetpack Compose (Material 3), [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (bundles yt-dlp, Python, FFmpeg and aria2c). Typeface: [Inter](https://rsms.me/inter) (SIL OFL 1.0).

## Building

Every push is built by GitHub Actions - grab the APK from the workflow artifacts.

Locally: `./gradlew assembleDebug` (JDK 17, Android SDK 35).
