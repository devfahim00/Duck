# Duck v1.0.0

The first official release. Duck is a yt-dlp powered video downloader for
Android — paste a link, pick a quality, download at full speed.

## What's new in v1.0.0

- **Automatic app updates** — Duck now checks GitHub Releases silently on
  every launch and offers the new APK with a one-tap download when a newer
  version is published.
- **Manual update check** — a new *App updates* section in Settings with a
  "Check for updates" button, so you can look for a new version any time.
- **Telegram** — news, updates and support from the owner at
  [@droxilen](https://t.me/droxilen), linked from Settings.
- **Up to 32 download threads** — the connections-per-download setting now
  goes up to 32 (4 / 8 / 16 / 32) for maximum speed on fast networks.

## Features

- **Paste-a-link downloading** — paste a link from YouTube or any of the
  1000+ sites supported by yt-dlp and Duck fetches the available formats.
- **Quality selection** — Best / 4K / 1080p / 720p / lower / audio-only.
- **Automatic muxing** — separate video + audio streams are merged
  automatically with the bundled FFmpeg.
- **Built-in download manager** — queue, live progress, speed and ETA,
  cancel / retry, and download history that survives app restarts.
- **Multi-threaded downloading** for extra speed:
  - concurrent fragment downloads (yt-dlp `-N`), now up to **32 threads**
  - optional **Turbo mode** using the bundled aria2c multi-connection
    downloader
- **Multiple simultaneous downloads** — 1, 2 or 3 downloads at once.
- **Share-to-Duck** — share links from any app straight into Duck.
- **In-app engine updates** — update the bundled yt-dlp engine from
  Settings; pinned to yt-dlp `2026.08.19` for a known-good, tested build.
- **Automatic app update checks** on launch, plus manual checks in
  Settings.
- **Telegram support channel** — reach the owner at
  [@droxilen](https://t.me/droxilen).
- **Live progress that actually works** — real-time percentage, speed and
  ETA instead of sitting at 0% until completion.
- **Ultra-modern UI** — dark "midnight glass" design with Inter
  typography, gradient progress bars with shimmer, floating pill
  navigation, animated tab transitions, and quality badges.
- **arm64-v8a only build** — smaller APK, built and signed by CI.

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

## Updating

Duck checks for updates automatically on launch. You can also check
manually from Settings → *App updates*. New APKs are published right here
on the [Releases page](https://github.com/devfahim00/Duck/releases).
