// Top-level build file for Duck
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Chaquopy: Python-for-Android SDK. Free + open-source (MIT) since v12.0.1,
    // published on Maven Central - no chaquo.com repo or license key needed.
    // Used to bundle a real CPython 3.13 runtime + yt-dlp + curl_cffi, so
    // yt-dlp's `impersonate=` (TLS/JA3 fingerprinting) actually has a target
    // on Android. See ytdlp/ChaquopyDiagnostics.kt for why this exists.
    id("com.chaquo.python") version "17.0.0" apply false
}
