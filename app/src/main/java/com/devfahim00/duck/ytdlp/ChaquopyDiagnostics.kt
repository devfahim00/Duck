package com.devfahim00.duck.ytdlp

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * STAGE 1 of the curl_cffi/Chaquopy migration.
 *
 * Deliberately NOT wired into [YtDlpEngine], [YtDlpUpdater] or
 * [com.devfahim00.duck.downloads.DownloadManager] yet - the existing
 * youtubedl-android engine keeps running exactly as before. This object
 * exists only so CI proves two things that a Gradle-only pip resolve can't:
 *
 *  1. The Chaquopy plugin + a real CPython 3.13 runtime + yt-dlp + curl_cffi
 *     actually link and package into an arm64-v8a APK (not just "pip found
 *     a wheel on PyPI" - the wheel's native .so has to survive Chaquopy's
 *     own packaging step too).
 *  2. `Python.start()` + calling into the bundled `ytdlp_bridge` module
 *     compiles against the real Chaquopy Java API, not a guess at its
 *     shape.
 *
 * Call [runDiagnostics] manually (e.g. temporarily from a debug menu item
 * or adb shell / Logcat) on a real device to see whether curl_cffi actually
 * loaded and yt-dlp can see an impersonate target - that is the one thing
 * no CI build can prove. Once that comes back clean, the next commit
 * replaces the relevant parts of YtDlpEngine/YtDlpUpdater/DownloadManager
 * with real calls through this bridge, instead of this throwaway probe.
 */
object ChaquopyDiagnostics {

    /**
     * Starts the embedded Python runtime if needed and returns the JSON
     * string from `ytdlp_bridge.engine_diagnostics()`. Safe to call more
     * than once - [Python.start] is a no-op after the first call.
     */
    fun runDiagnostics(context: Context): String {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        val bridge = Python.getInstance().getModule("ytdlp_bridge")
        return bridge.callAttr("engine_diagnostics").toString()
    }
}
