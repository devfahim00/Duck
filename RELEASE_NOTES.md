# Duck (unreleased) - Chaquopy engine

Duck's yt-dlp now runs on a real CPython 3.13 (via Chaquopy) with `curl_cffi`,
instead of youtubedl-android's bundled Python 3.8.

- **Browser impersonation works.** yt-dlp can finally use `curl_cffi`, so
  extractors that need a real browser TLS/HTTP fingerprint (Pornhub,
  Instagram, TikTok, ...) get a Chrome fingerprint automatically. A new
  Settings switch, *Impersonate Chrome everywhere*, applies it to every request.
- **yt-dlp release channels.** Settings -> Engine & Updates lets you follow
  `stable`, `nightly` (default, what yt-dlp recommends) or `master`. Duck
  checks once a day, skips the download if you already have the newest tag,
  and never goes below the version bundled in the APK. A broken download is
  discarded automatically.
- **User-Agent:** Duck no longer forces an Android Chrome UA. yt-dlp's own
  current Chrome UA is used so it never contradicts the impersonated TLS
  fingerprint (the browser's UA is still sent when you signed in with the
  built-in browser, since those sessions are often UA-bound).
- **Turbo (aria2c) progress** is now read from aria2c's readout and reported
  through yt-dlp's progress hooks. Turbo is skipped while "Impersonate Chrome
  everywhere" is on (yt-dlp never hands impersonated requests to external
  downloaders).
- The pinned yt-dlp zipapp in `res/raw` is gone; the bundled copy now comes
  from pip at build time (`yt-dlp==2026.08.19`).

---

# Duck v1.2.0

The "actually works everywhere" release: the xnxx bug is fixed at the root,
and sites that need a login now work with a built-in browser (Seal-style)
instead of manual cookies.txt files.

## What's new in v1.2.0

- **Fixed the "I/O operation on closed file" error** (xnxx and other HLS
  sites). Root cause: the yt-dlp binary bundled inside the youtubedl-android
  library was exactly `2025.11.12` — the one release with a urllib
  regression (yt-dlp issue #15017) that breaks HLS downloads. On top of
  that, the in-app updater's file check rejected every real yt-dlp download
  because it expected the file to start with ZIP bytes while release
  zipapps start with a `#!` shebang — so updates silently never installed.
  Both are fixed:
  - Duck now **ships its own known-good yt-dlp (`2026.08.19`) inside the
    APK** and swaps it in on first launch with a local copy — works offline,
    no GitHub, no rate limits, no race with the first download.
  - The updater now validates the real zipapp format (shebang + ZIP magic).
- **Built-in browser for cookies (like Seal)** — Settings → *Cookies* →
  **Sign in with browser** opens an embedded browser: log in to Instagram /
  Facebook / xnxx or pass a site's age check, tap *Done*, and all cookies
  are imported automatically. No cookies.txt export, no browser extension.
  The browser's user-agent is remembered and sent together with the cookies
  (exactly what Seal does), so sessions that are bound to the UA keep
  working. Re-visiting a site refreshes its cookies; other sites are kept.
- **Stays current like Termux** — after the pinned engine is in place, Duck
  quietly refreshes to the **newest stable yt-dlp once a day** using
  GitHub's release redirect (never the rate-limited API), the same
  mechanism that made Termux work on sites the app failed on.
- **HLS quality picker fixed** — HLS formats that report a resolution but
  no codec (xnxx's `hls-1080p`, `hls-480p`…) now appear as real 1080p /
  720p / 480p options instead of hiding behind a single "Default" button,
  and single-file streams are preferred over merging when available.
- **Seal-parity download options** — `--no-mtime` and the cookie/UA
  combination now match what the Seal app sends on every download.

## Features

- **Paste-a-link downloading** — paste a link from YouTube or any of the
  1000+ sites supported by yt-dlp and Duck fetches the available formats.
- **Quality selection** — Best / 4K / 1080p / 720p / lower / audio-only.
- **Built-in cookie browser** — log in inside the app; cookies import
  automatically (or import a Netscape cookies.txt the classic way).
- **Automatic muxing** — separate video + audio streams are merged
  automatically with the bundled FFmpeg.
- **Built-in download manager** — queue, live progress, speed and ETA,
  cancel / retry, and download history that survives app restarts.
- **Multi-threaded downloading** for extra speed:
  - concurrent fragment downloads (yt-dlp `-N`), now up to **32 threads**
  - optional **Turbo mode** using the bundled aria2c engine
- **Auto-updating engine and app** — yt-dlp refreshes itself daily from the
  latest stable release; the app checks GitHub Releases on every launch.
- **Signed releases & in-app updates** — APKs are signed in CI and Duck
  offers one-tap updates from GitHub Releases.

## Downloads

Grab `Duck-arm64-v8a-v1.2.0.apk` from the release assets (arm64-v8a, covers
virtually all modern Android phones). Previous versions are available from
the GitHub Releases page.
