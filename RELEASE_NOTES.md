# Duck v0.0.1

First signed release. Duck is a yt-dlp powered video downloader for Android.

## Features

- **Paste-a-link downloading** — paste a link from YouTube or any of the
  1000+ sites supported by yt-dlp and Duck fetches the available formats.
- **Quality selection** — Best / 4K / 1080p / 720p / lower / audio-only.
- **Automatic muxing** — separate video + audio streams are merged
  automatically with the bundled FFmpeg.
- **Built-in download manager** — queue, live progress, speed and ETA,
  cancel / retry, and download history.
- **Multi-threaded downloading** for extra speed:
  - concurrent fragment downloads (yt-dlp `-N`)
  - optional **Turbo mode** using the bundled aria2c multi-connection
    downloader
- **Multiple simultaneous downloads.**
- **Share-to-Duck** — share links from any app straight into Duck.
- **In-app engine updates** — update the bundled yt-dlp engine from
  Settings; the app is currently pinned to yt-dlp `2026.08.19` for a
  known-good, tested build.
- **Live progress that actually works** — real-time percentage, speed and
  ETA instead of sitting at 0% until completion.
- **Ultra-modern UI** — dark "midnight glass" design with Inter
  typography, gradient progress bars with shimmer, floating pill
  navigation, animated tab transitions, and quality badges.
- **arm64-v8a only build** — smaller APK, built and signed by CI on every
  push to `main`.

## Supported websites

Powered by yt-dlp's extractor library (1700+ extractors, 1000+ sites),
including all the major platforms out of the box:

YouTube, Facebook, Instagram, TikTok, Twitter/X, Vimeo, Dailymotion,
Twitch, Reddit, SoundCloud, and many, many more — see yt-dlp's own
[supported sites list](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)
for the full, up-to-date list.

## Tech

Kotlin, Jetpack Compose (Material 3), youtubedl-android (bundles yt-dlp,
Python, FFmpeg and aria2c). Typeface: Inter (SIL OFL 1.0).

## Build

Signed and built automatically by GitHub Actions on every push to `main`.
