# Duck v1.1.0

The "works everywhere" release: the engine now keeps itself current, and
sites that need a login are unlocked with cookies.

## What's new in v1.1.0

- **Self-updating yt-dlp engine** — on every launch Duck silently brings its
  yt-dlp binary up to the pinned release (`2026.08.19`) in the background.
  Previously the engine only updated if you found the button in Settings, so
  most installs were stuck on the months-old bundled build — the reason many
  sites worked in Termux but not in Duck. Fetches and downloads now also wait
  (up to 60s) for the first-launch update to finish, so links are analyzed
  with a current engine from the very first tap.
- **Cookies support (like Seal)** — import a Netscape `cookies.txt` in
  Settings → *Cookies* and it is attached to every fetch and download. This
  unlocks login-walled content: Instagram, Facebook, age-restricted YouTube,
  "confirm you're not a bot" checks, and similar. Cookies can be toggled off
  or removed at any time, and import errors explain exactly what went wrong.
- **Fewer network failures** — requests now run with
  `--no-check-certificate` (some sites/devices fail TLS verification), a real
  yt-dlp cache directory (faster, more reliable YouTube extraction — same as
  desktop/Termux behavior), more retries, and extractor retries.
- **Friendlier errors** — when a site asks for cookies, a login, or bot
  verification, the error card now points you straight to Settings → Cookies
  instead of showing a raw yt-dlp message.
- **Honest engine version** — Settings now runs `yt-dlp --version` for real
  and always shows the true engine version.

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
- **Self-updating yt-dlp engine** — silently brought to the pinned release
  (`2026.08.19`) on every launch; manual reinstall available in Settings.
- **Cookies support** — import a `cookies.txt` for login-walled sites, with
  toggle and one-tap removal.
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
