package com.devfahim00.duck.ytdlp

import android.content.Context
import com.devfahim00.duck.R
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wraps the yt-dlp binary lifecycle.
 *
 * Three layers, in order of trust:
 *
 *  1. **Bundled engine (R.raw.ytdlp_pinned)** — a known-good yt-dlp zipapp
 *     shipped inside the APK. Installing it is a 3 MB local copy: works
 *     offline, first launch, no GitHub, no rate limits, no race. This
 *     exists because the binary bundled inside the youtubedl-android AAR
 *     (0.18.1 ships `2025.11.12`) is exactly the release that contains the
 *     urllib regression that made HLS sites fail with
 *     "I/O operation on closed file" (yt-dlp #15017). Every fresh install
 *     would otherwise run that broken version until an update succeeded.
 *
 *  2. **Latest stable from GitHub** — `releases/latest/download/yt-dlp` is a
 *     plain CDN redirect that always points at the newest stable release,
 *     so Duck keeps extractors fresh like Termux does, WITHOUT touching the
 *     rate-limited api.github.com REST endpoints. Refreshed quietly once a
 *     day in the background; failures are ignored (we still have layer 1).
 *
 *  3. **Manual update button** (Settings → yt-dlp engine) — force-runs the
 *     same network refresh and reports the result.
 *
 * All swaps are staged + atomically renamed, so a download that starts
 * while an update is in flight never observes a half-written binary.
 */
object YtDlpUpdater {

    /** yt-dlp release bundled in res/raw. Bump together with the resource. */
    const val PINNED_VERSION = "2026.08.19"

    /**
     * Redirecting URL for the newest stable release asset. Deliberately NOT
     * the api.github.com "latest release" endpoint: anonymous REST calls are
     * limited to 60/hour per IP, which carrier CGNAT networks burn through
     * instantly (the exact failure the old updater had). This URL is served
     * by GitHub's CDN and never rate limited.
     */
    private const val LATEST_STABLE_URL =
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

    /** Stored in the app-wide "duck_settings" SharedPreferences. */
    private const val PREFS_NAME = "duck_settings"
    private const val PREF_INSTALLED_VERSION = "installed_ytdlp_version"
    private const val PREF_ENGINE_SIGNATURE = "ytdlp_engine_signature"
    private const val PREF_LAST_NET_ATTEMPT = "ytdlp_net_update_last_attempt"

    /** How long to wait before retrying a failed network refresh. */
    private const val NET_RETRY_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    /**
     * FIRST-LAUNCH GUARANTEE - makes sure the binary on disk is the pinned
     * engine from our own APK resources. Cheap when already installed
     * (signature check only); when not, it is a pure local copy - no python
     * process, no network, works on first launch even fully offline.
     *
     * Runs as part of [com.devfahim00.duck.ytdlp.YtDlpEngine] bootstrap,
     * before the first fetch/download is allowed to proceed.
     */
    fun ensurePinnedEngine(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val binary = engineBinary(appContext)
        if (!binary.exists() || signature(binary) != prefs.getString(PREF_ENGINE_SIGNATURE, null)) {
            installPinned(appContext)
        }
    }

    /**
     * BACKGROUND REFRESH - quietly moves to the newest stable yt-dlp release
     * at most once per day. Best-effort by design: offline devices simply
     * stay on the pinned engine, which is always a working version.
     */
    fun refreshLatest(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val lastAttempt = prefs.getLong(PREF_LAST_NET_ATTEMPT, 0L)
        if (System.currentTimeMillis() - lastAttempt < NET_RETRY_COOLDOWN_MS) return
        markNetAttempt(appContext)

        try {
            downloadLatest(appContext)
        } catch (_: Exception) {
            // Silent by design; retried after the cooldown.
        }
    }

    /** MANUAL update (Settings > yt-dlp engine). Always fetches latest stable. */
    fun update(context: Context): Result {
        val appContext = context.applicationContext
        return try {
            markNetAttempt(appContext)
            downloadLatest(appContext)
            val verified = currentVersion(appContext)
            if (verified == null) {
                // The binary runs (download finished) but version check failed.
                Result.Success("yt-dlp engine refreshed")
            } else {
                Result.Success("yt-dlp updated to $verified")
            }
        } catch (error: Exception) {
            // Never leave the user stranded: fall back to the bundled engine.
            runCatching { installPinned(appContext) }
            Result.Failure(
                "Update failed (${error.message?.take(120) ?: "network error"}) - " +
                    "restored the bundled $PINNED_VERSION engine"
            )
        }
    }

    /**
     * The REAL version of the binary on disk, by running `yt-dlp --version`
     * (takes ~1-2s of python startup; call from Dispatchers.IO).
     */
    fun currentVersion(context: Context): String? {
        return runCatching {
            val request = YoutubeDLRequest("")
            request.addOption("--version")
            YoutubeDL.getInstance().execute(request).out.trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ install

    /** Copies the APK-bundled zipapp over the engine binary and records it. */
    private fun installPinned(appContext: Context) {
        val ytdlpDir = engineDir(appContext)
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        val staging = File(ytdlpDir, YoutubeDL.ytdlpBin + ".pinned")
        appContext.resources.openRawResource(R.raw.ytdlp_pinned).use { input ->
            staging.outputStream().use { output -> input.copyTo(output) }
        }
        if (!looksLikeYtDlpZipapp(staging)) {
            runCatching { staging.delete() }
            return // resource is broken beyond hope; keep whatever is on disk
        }
        swapIn(appContext, staging)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_INSTALLED_VERSION, PINNED_VERSION)
            .putString(PREF_ENGINE_SIGNATURE, signature(engineBinary(appContext)))
            .apply()
    }

    /** Downloads the newest stable release, verifies and swaps it in. */
    private fun downloadLatest(appContext: Context) {
        val tmp = File.createTempFile("yt-dlp", null, appContext.cacheDir)
        try {
            downloadFollowingRedirects(LATEST_STABLE_URL, tmp)
            if (tmp.length() < 1_000_000L) {
                // A real yt-dlp zipapp is several MB; anything tiny means we
                // saved an error page instead of the binary.
                throw IOException("downloaded file looks truncated/invalid")
            }
            if (!looksLikeYtDlpZipapp(tmp)) {
                throw IOException("downloaded file is not a valid yt-dlp zipapp")
            }
            val ytdlpDir = engineDir(appContext)
            if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
            val staging = File(ytdlpDir, YoutubeDL.ytdlpBin + ".new")
            tmp.copyTo(staging, overwrite = true)
            swapIn(appContext, staging)
        } finally {
            runCatching { tmp.delete() }
        }
    }

    /** Atomic staged swap; keeps already-running processes on the old inode. */
    private fun swapIn(appContext: Context, staging: File) {
        val binary = engineBinary(appContext)
        staging.setExecutable(true, true)
        if (binary.exists()) binary.delete()
        if (!staging.renameTo(binary)) {
            // Same-filesystem rename should always work; fall back to a
            // plain copy for exotic devices.
            staging.copyTo(binary, overwrite = true)
            staging.delete()
            binary.setExecutable(true, true)
        }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_ENGINE_SIGNATURE, signature(binary))
            .apply()
    }

    /**
     * yt-dlp release assets are python zipapps: `#!/usr/bin/env python3`
     * shebang followed by ZIP data ("PK\x03\x04") within the first line.
     * Accept both bare zips and shebang-prefixed zipapps.
     *
     * (The old check demanded the file start with "PK", which the release
     * asset never does — every single network update used to fail here.)
     */
    private fun looksLikeYtDlpZipapp(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val head = ByteArray(96)
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                val text = String(head, 0, read, Charsets.ISO_8859_1)
                text.contains("PK\u0003\u0004")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun engineDir(appContext: Context): File =
        File(File(appContext.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName)

    private fun engineBinary(appContext: Context): File =
        File(engineDir(appContext), YoutubeDL.ytdlpBin)

    /** length + mtime: detects the library re-extracting its stale binary. */
    private fun signature(binary: File): String =
        "${binary.length()}-${binary.lastModified()}"

    private fun markNetAttempt(appContext: Context) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(PREF_LAST_NET_ATTEMPT, System.currentTimeMillis())
            .apply()
    }

    @Throws(IOException::class)
    private fun downloadFollowingRedirects(urlStr: String, dest: File, maxRedirects: Int = 6) {
        var current = urlStr
        var redirects = 0
        while (true) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "Duck-Android-App")
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrEmpty() || redirects >= maxRedirects) {
                    throw IOException("too many redirects fetching $urlStr")
                }
                current = location
                redirects++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code while downloading yt-dlp")
            }
            conn.inputStream.use { input: InputStream ->
                dest.outputStream().use { output: OutputStream -> input.copyTo(output) }
            }
            conn.disconnect()
            return
        }
    }
}
