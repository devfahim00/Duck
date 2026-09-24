<div align="center">

# 🦆 Duck

**A fast, yt-dlp powered video downloader for Android — built with Kotlin & Jetpack Compose.**

No account. No API keys. No ads. Just downloads.

<br>

[![Total Downloads](https://img.shields.io/github/downloads/devfahim00/Duck/total?style=for-the-badge&logo=github&label=Total%20Downloads&color=success)](https://github.com/devfahim00/Duck/releases)
[![Latest Release](https://img.shields.io/github/v/release/devfahim00/Duck?style=for-the-badge&logo=github&label=Latest%20Release&color=blue)](https://github.com/devfahim00/Duck/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/devfahim00/Duck/android.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=CI)](https://github.com/devfahim00/Duck/actions/workflows/android.yml)

<br>

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Material 3](https://img.shields.io/badge/Material%203-757575?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![yt-dlp](https://img.shields.io/badge/yt--dlp-powered-241800?style=for-the-badge)](https://github.com/yt-dlp/yt-dlp)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![JDK 17](https://img.shields.io/badge/JDK-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

[![GitHub stars](https://img.shields.io/github/stars/devfahim00/Duck?style=flat-square&logo=github&color=yellow)](https://github.com/devfahim00/Duck/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/devfahim00/Duck?style=flat-square&logo=github&color=blue)](https://github.com/devfahim00/Duck/network/members)
[![GitHub issues](https://img.shields.io/github/issues/devfahim00/Duck?style=flat-square&logo=github&color=red)](https://github.com/devfahim00/Duck/issues)
[![Last commit](https://img.shields.io/github/last-commit/devfahim00/Duck?style=flat-square&logo=git&logoColor=white&color=green)](https://github.com/devfahim00/Duck/commits/main)
[![Repo size](https://img.shields.io/github/repo-size/devfahim00/Duck?style=flat-square&color=orange)](https://github.com/devfahim00/Duck)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=flat-square)](#-contributing)

[**Download**](#-download) •
[**Features**](#-features) •
[**App Updates**](#-app-updates) •
[**Getting Started**](#-getting-started) •
[**Tech Stack**](#-tech-stack) •
[**Contributing**](#-contributing)

</div>

---

## 📑 Table of Contents

- [About](#-about)
- [Features](#-features)
- [Supported Websites](#-supported-websites)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Download](#-download)
- [App Updates](#-app-updates)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Clone & Build](#clone--build)
- [Continuous Integration](#-continuous-integration)
- [Roadmap](#-roadmap)
- [Troubleshooting](#-troubleshooting)
- [FAQ](#-faq)
- [Contributing](#-contributing)
- [Disclaimer](#-disclaimer)
- [Credits & Acknowledgements](#-credits--acknowledgements)
- [Contact](#-contact)

---

## 📖 About

**Duck** is a video downloader for Android, written in **Kotlin** and powered by
[**yt-dlp**](https://github.com/yt-dlp/yt-dlp) — the most capable open-source video
downloader on the planet, supporting **1000+ websites** out of the box.

It is designed around three things: **speed** — up to 32 concurrent connections per
download plus an optional aria2c-powered **Turbo mode**; **privacy** — no account, no
API keys, no ads and no tracking, with history and settings stored only on your device;
and a modern **dark "midnight glass" UI** built with Jetpack Compose and Material 3.

Downloads land in the public **Downloads/Duck** folder, the built-in download manager
shows live progress, speed and ETA, and the app keeps itself fresh with automatic update
checks against GitHub Releases.

---

## ✨ Features

| | Feature | Description |
|---|---|---|
| 📋 | **Paste-a-link downloading** | Paste a link from YouTube or any of the 1000+ sites supported by yt-dlp |
| 🎯 | **Quality selection** | Best / 4K / 1080p / 720p / lower / audio-only |
| 🎬 | **Automatic muxing** | Separate video + audio streams merged with the bundled FFmpeg |
| 📥 | **Built-in download manager** | Queue, live progress, speed & ETA, cancel / retry, restart-proof history |
| ⚡ | **Up to 32 threads** | 4 / 8 / 16 / 32 concurrent connections per download (yt-dlp `-N`) |
| 🚀 | **Turbo mode** | Optional aria2c segmented multi-connection downloader for maximum speed |
| 🔀 | **Simultaneous downloads** | Run multiple downloads at the same time |
| 🔄 | **Auto app updates** | Silent GitHub Releases check on every launch, with a one-tap download |
| 🔎 | **Manual update check** | "Check for updates" any time from Settings → App updates |
| 🧠 | **In-app engine updates** | Update the bundled yt-dlp engine straight from Settings |
| 🔗 | **Share-to-Duck** | Share links from any app straight into Duck |
| 🌙 | **Midnight glass UI** | Dark Material 3 design, Inter typography, gradient progress bars with shimmer |
| 📱 | **arm64-v8a build** | One lean ~65 MB APK covering virtually all modern Android phones |

---

## 🌐 Supported Websites

Duck supports every site yt-dlp supports — **1000+ websites** and counting, including:

YouTube, Facebook, Instagram, TikTok, Twitter/X, Vimeo, Dailymotion, Twitch, Reddit,
SoundCloud, and many, many more.

See yt-dlp's own
[supported sites list](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)
for the full, up-to-date list.

---

## 🛠 Tech Stack

[![Kotlin Coroutines](https://img.shields.io/badge/Kotlin-Coroutines-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/coroutines-overview.html)
[![yt-dlp](https://img.shields.io/badge/yt--dlp-2026.08.19-241800?style=flat-square)](https://github.com/yt-dlp/yt-dlp)
[![youtubedl-android](https://img.shields.io/badge/youtubedl--android-0.18.1-DA3D3D?style=flat-square)](https://github.com/JunkFood02/youtubedl-android)
[![FFmpeg](https://img.shields.io/badge/FFmpeg-Muxing-007808?style=flat-square&logo=ffmpeg&logoColor=white)](https://ffmpeg.org/)
[![aria2c](https://img.shields.io/badge/aria2c-Turbo%20Downloader-8B3A3A?style=flat-square)](https://aria2.github.io/)
[![Coil](https://img.shields.io/badge/Coil-Image%20Loading-FF6F00?style=flat-square)](https://coil-kt.github.io/coil/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![GitHub Actions](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/devfahim00/Duck/actions)

| Layer | Technology |
|---|---|
| **Language** | Kotlin with Coroutines |
| **Download engine** | [yt-dlp](https://github.com/yt-dlp/yt-dlp) `2026.08.19` (pinned) via [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) `0.18.1` |
| **Bundled runtime** | Python, FFmpeg and aria2c — shipped inside the APK |
| **Muxing** | [FFmpeg](https://ffmpeg.org/) — automatic video + audio merge |
| **Image loading** | [Coil](https://coil-kt.github.io/coil/) |
| **UI** | Jetpack Compose, Material Design 3 (dark "midnight glass" theme) |
| **Typography** | [Inter](https://rsms.me/inter) (SIL OFL 1.0) |
| **Build system** | Gradle (Kotlin DSL) |
| **CI** | GitHub Actions — signed release builds + automated Releases |

---

## 🗂 Project Structure

```text
Duck/
├── .github/
│   └── workflows/        # CI pipeline — signed release APK; publishes Releases on v* tags
├── app/                  # Android application module
│   └── src/main/         # Kotlin sources, Compose UI, resources, manifest
├── docs/                 # Project artwork
├── gradle/
│   └── wrapper/          # Gradle wrapper files
├── build.gradle.kts      # Root build configuration
├── settings.gradle.kts   # Project & module settings
├── gradle.properties     # Gradle / Android build properties
├── RELEASE_NOTES.md      # Changelog used as the GitHub Release body
├── gradlew               # Gradle wrapper (Unix)
├── gradlew.bat           # Gradle wrapper (Windows)
└── README.md
```

---

## 📥 Download

Get the latest pre-built, CI-signed APK straight from the **Releases** page — no build required.

<div align="center">

[![Download Latest APK](https://img.shields.io/badge/Download-Latest%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/devfahim00/Duck/releases/latest)

[![Total Downloads](https://img.shields.io/github/downloads/devfahim00/Duck/total?style=flat-square&logo=github&label=Total%20Downloads&color=success)](https://github.com/devfahim00/Duck/releases)
[![Latest Release Downloads](https://img.shields.io/github/downloads/devfahim00/Duck/latest/total?style=flat-square&logo=github&label=Latest%20Release%20Downloads&color=brightgreen)](https://github.com/devfahim00/Duck/releases/latest)
[![Version](https://img.shields.io/github/v/release/devfahim00/Duck?style=flat-square&label=Version&color=blue)](https://github.com/devfahim00/Duck/releases/latest)
[![Release Date](https://img.shields.io/github/release-date/devfahim00/Duck?style=flat-square&label=Released&color=orange)](https://github.com/devfahim00/Duck/releases/latest)

</div>

**Installation steps**

1. Open the [**latest release**](https://github.com/devfahim00/Duck/releases/latest).
2. Under **Assets**, download the `Duck-arm64-v8a-v…apk` file.
3. On your Android device, allow **Install unknown apps** for your browser or file manager.
4. Open the downloaded APK and tap **Install**.

> Duck ships as an **arm64-v8a** APK — compatible with virtually every modern Android
> phone (any 64-bit device on Android 8.0+). Every release's notes carry the full
> changelog of what's inside the build.

---

## 🔄 App Updates

Duck keeps itself up to date in two ways:

- **Automatic** — on every launch, Duck silently checks GitHub Releases and pops a dialog
  with a one-tap download whenever a newer version is published.
- **Manual** — open Settings → **App updates** → **Check for updates** any time you want,
  and download the new APK right from there.

News about new releases also lands on the owner's Telegram:
[@droxilen](https://t.me/droxilen).

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version |
|---|---|
| **Android Studio** | Latest stable release (recommended) |
| **JDK** | 17 |
| **Android SDK** | 35 (installed via Android Studio's SDK Manager) |
| **Git** | Any recent version |

### Clone & Build

```bash
# 1. Clone the repository
git clone https://github.com/devfahim00/Duck.git
cd Duck

# 2. Build a debug APK (Linux / macOS)
./gradlew assembleDebug

# ...or on Windows
gradlew.bat assembleDebug
```

The generated APK will be located at:

```text
app/build/outputs/apk/debug/
```

**Using Android Studio**

1. Open **Android Studio → File → Open** and select the `Duck` folder.
2. Wait for the Gradle sync to complete (make sure the Gradle JDK is set to **17**).
3. Connect a device or start an emulator.
4. Press **Run ▶️**.

---

## 🤖 Continuous Integration

Every push to GitHub is built by **GitHub Actions**:

- Every push builds a **signed release APK** (arm64-v8a) and uploads it as a workflow
  artifact.
- Pushing a **`v*` tag** publishes a **GitHub Release** with the signed APK attached and
  the full changelog from `RELEASE_NOTES.md`.

See the [Actions tab](https://github.com/devfahim00/Duck/actions) for build history.

---

## 🗺 Roadmap

> Ideas under consideration — feel free to open an issue to discuss or vote on them.

- [ ] Playlist support
- [ ] In-app APK download & guided install
- [ ] Subtitle downloads
- [ ] More quality / codec options
- [ ] Light theme / dynamic color
- [ ] Localization (including Bangla 🇧🇩)
- [ ] Universal APK for older 32-bit devices

---

## 🧯 Troubleshooting

<details>
<summary><b>Downloads are saved to a private folder</b></summary>

<br>

Grant Duck **All Files Access** so downloads land in the public
`Downloads/Duck` folder, where every app and your gallery can see them:
open the app and follow the storage prompt, or Settings → Storage →
**Grant access**.

</details>

<details>
<summary><b>A site suddenly fails or "no downloadable formats found"</b></summary>

<br>

Extraction can break when a website changes its internals. Open
Settings → **yt-dlp engine** → **Update yt-dlp** to pull the pinned,
known-good engine build. If it persists, check whether a newer app release
is available and report the URL via an issue or Telegram.

</details>

<details>
<summary><b>Downloads feel slow</b></summary>

<br>

Raise the connection count — Settings → **Download threads** → 16 or 32 —
and consider enabling **Turbo mode** (aria2c segmented downloading). Both
help a lot on fast networks.

</details>

<details>
<summary><b>Update check says "up to date" but a newer release exists</b></summary>

<br>

GitHub's `releases/latest` redirect can cache briefly. Wait a few minutes
and check again, or grab the APK directly from the
[Releases page](https://github.com/devfahim00/Duck/releases/latest).

</details>

---

## ❓ FAQ

<details>
<summary><b>Is Duck free?</b></summary>

<br>

Yes — completely. No ads, no account, no in-app purchases, no tracking.

</details>

<details>
<summary><b>Which devices are supported?</b></summary>

<br>

Any modern 64-bit (arm64-v8a) Android phone running Android 8.0 or newer.

</details>

<details>
<summary><b>Where are my downloads saved?</b></summary>

<br>

In the public `Downloads/Duck` folder when All Files Access is granted;
otherwise in a Duck-private folder only the app can open.

</details>

<details>
<summary><b>Do I need an account or API key?</b></summary>

<br>

No. Duck works anonymously through yt-dlp — nothing to sign up for,
nothing to configure.

</details>

<details>
<summary><b>Is downloading videos legal?</b></summary>

<br>

That depends on the content and your local laws. Duck is a tool — use it
for personal, legitimate downloads and respect each website's Terms of
Service. See the [Disclaimer](#-disclaimer).

</details>

<details>
<summary><b>Is Duck affiliated with yt-dlp or any video platform?</b></summary>

<br>

No. Duck is an independent project. See the [Disclaimer](#-disclaimer).

</details>

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. **Fork** the repository
2. **Create** your feature branch
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Commit** your changes
   ```bash
   git commit -m "feat: add amazing feature"
   ```
4. **Push** to your branch
   ```bash
   git push origin feature/amazing-feature
   ```
5. **Open a Pull Request**

Please keep code style consistent with the existing Kotlin codebase, and make sure
`./gradlew assembleDebug` passes before submitting.

Found a broken site or a bug? [Open an issue](https://github.com/devfahim00/Duck/issues/new)
with the URL, steps to reproduce, your device model, and Android version.

---

## ⚠️ Disclaimer

> **This project is for educational purposes only.**
> Duck is **not affiliated with, endorsed by, or connected to yt-dlp, YouTube, Google,
> or any content platform** in any way.
> All trademarks and brand names belong to their respective owners.
> Please respect each website's Terms of Service and your local laws when using this software.
> The developer is not responsible for any misuse of this project.

---

## 🙏 Credits & Acknowledgements

- [**yt-dlp**](https://github.com/yt-dlp/yt-dlp) — the download engine that powers it all
- [**youtubedl-android**](https://github.com/JunkFood02/youtubedl-android) — yt-dlp, Python, FFmpeg and aria2c bundled for Android
- [**FFmpeg**](https://ffmpeg.org/) — stream muxing
- [**aria2c**](https://aria2.github.io/) — Turbo-mode segmented downloads
- [**Coil**](https://coil-kt.github.io/coil/) — image loading
- [**Jetpack Compose / Material 3**](https://m3.material.io/) — design system
- [**Inter**](https://rsms.me/inter) (SIL OFL 1.0) — typography
- [**Shields.io**](https://shields.io/) — README badges

---

## 📬 Contact

**Fahim** — Developer

[![GitHub](https://img.shields.io/badge/GitHub-devfahim00-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/devfahim00)
[![Telegram](https://img.shields.io/badge/Telegram-@droxilen-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/droxilen)

Project Link: [https://github.com/devfahim00/Duck](https://github.com/devfahim00/Duck)

<div align="center">

<br>

**If you like Duck, please consider giving it a ⭐ — it really helps!**

Made with ❤️ in Bangladesh 🇧🇩

</div>
