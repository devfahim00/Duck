package com.devfahim00.duck.ytdlp

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wraps the yt-dlp binary lifecycle.
 *
 * The app is pinned to a specific yt-dlp release ([PINNED_VERSION]) rather than
 * whatever GitHub reports as "latest": a known-good version avoids surprise
 * regressions and lets us test one exact build. All update paths therefore
 * avoid the library's own "latest" updater (which hits the rate-limited
 * api.github.com/.../releases/latest endpoint) and instead (re)install
 * [PINNED_VERSION] via a direct, non-API download.
 *
 * Why not the library's own updater: youtubedl-android's updater hits
 * https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest directly and
 * unauthenticated. GitHub allows only 60 anonymous requests/hour *per source
 * IP*. On carrier mobile networks (very common for our BD users, heavy CGNAT)
 * huge numbers of subscribers share one public IP, so that quota is exhausted
 * almost immediately and the call fails with an opaque "failed to update
 * youtube-dl".
 *
 * Instead we fetch the pinned tag's asset directly
 * (github.com/.../releases/download/$PINNED_VERSION/yt-dlp), a plain CDN
 * redirect, NOT the rate-limited REST API, so it keeps working even when
 * api.github.com is throttled. We drop the result into the exact directory the
 * bundled library already expects, so the rest of the app (YtDlpEngine,
 * currentVersion()) keeps working unmodified.
 *
 * IMPORTANT: the yt-dlp that ships *inside* the app (res/raw of the
 * youtubedl-android AAR) is months old at install time, and site extractors
 * rot within weeks - that is why "it works in Termux (latest yt-dlp) but not
 * in the app". [ensureLatest] fixes that by auto-installing the pinned
 * release once, quietly, on every app start; users no longer need to find the
 * hidden manual update button.
 */
object YtDlpUpdater {

    /** yt-dlp release this app is pinned to. Bump this string to move to a new version. */
    const val PINNED_VERSION = "2026.08.19"

    private const val DIRECT_DOWNLOAD_URL =
        "https://github.com/yt-dlp/yt-dlp/releases/download/$PINNED_VERSION/yt-dlp"

    /** Stored in the app-wide "duck_settings" SharedPreferences. */
    private const val PREFS_NAME = "duck_settings"
    private const val PREF_INSTALLED_VERSION = "installed_ytdlp_version"
    private const val PREF_LAST_ATTEMPT = "ytdlp_update_last_attempt"

    /** How long to wait before retrying a failed auto-update. */
    private const val RETRY_COOLDOWN_MS = 30 * 60 * 1000L

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    /**
     * AUTO-UPDATE - called once per app start from [YtDlpEngine.prewarm],
     * after the runtime has been extracted.
     *
     * Installs [PINNED_VERSION] only when the recorded installed version does
     * not match, and never more often than every [RETRY_COOLDOWN_MS] when
     * attempts keep failing (e.g. offline device). On success the version is
     * verified by actually running `yt-dlp --version` before it is recorded,
     * so a truncated/corrupt download can never be remembered as "installed".
     *
     * Silently no-ops on every subsequent launch.
     */
    fun ensureLatest(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(PREF_INSTALLED_VERSION, null) == PINNED_VERSION) return

        val lastAttempt = prefs.getLong(PREF_LAST_ATTEMPT, 0L)
        if (System.currentTimeMillis() - lastAttempt < RETRY_COOLDOWN_MS) return
        markAttempt(appContext)

        try {
            downloadDirect(appContext)
            val verified = currentVersion(appContext)
            if (verified != PINNED_VERSION) {
                throw IOException(
                    "verification returned '${verified ?: "no version"}' instead of $PINNED_VERSION"
                )
            }
            prefs.edit().putString(PREF_INSTALLED_VERSION, PINNED_VERSION).apply()
        } catch (error: Exception) {
            // Stay on the current binary; retried on a later launch after the
            // cooldown. Not logged on purpose - this runs on every offline
            // start and must stay completely silent.
        }
    }

    /** MANUAL update (Settings > yt-dlp engine). Always (re)installs [PINNED_VERSION]. */
    fun update(context: Context): Result {
        val appContext = context.applicationContext
        return try {
            downloadDirect(appContext)
            val verified = currentVersion(appContext)
            if (verified != PINNED_VERSION) {
                throw IOException(
                    "verification returned '${verified ?: "no version"}' instead of $PINNED_VERSION"
                )
            }
            markInstalled(appContext)
            Result.Success("yt-dlp updated to $PINNED_VERSION")
        } catch (error: Exception) {
            Result.Failure("Update failed: ${error.message?.take(160) ?: "unknown error"}")
        }
    }

    /**
     * The REAL version of the binary on disk, by running `yt-dlp --version`
     * (takes ~1-2s of python startup; call from Dispatchers.IO).
     *
     * Note YoutubeDL.getInstance().version() is useless for us: it only echoes
     * a SharedPrefs string written by the library's own GitHub-API updater,
     * which this app never uses - so it always returns null here.
     */
    fun currentVersion(context: Context): String? {
        return runCatching {
            val request = YoutubeDLRequest("")
            request.addOption("--version")
            YoutubeDL.getInstance().execute(request).out.trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun markInstalled(appContext: Context) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_INSTALLED_VERSION, PINNED_VERSION)
            .putLong(PREF_LAST_ATTEMPT, System.currentTimeMillis())
            .apply()
    }

    private fun markAttempt(appContext: Context) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(PREF_LAST_ATTEMPT, System.currentTimeMillis())
            .apply()
    }

    private fun downloadDirect(context: Context) {
        val appContext = context.applicationContext
        val ytdlpDir = File(File(appContext.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName)
        val binary = File(ytdlpDir, YoutubeDL.ytdlpBin)
        val tmp = File.createTempFile("yt-dlp", null, appContext.cacheDir)
        try {
            downloadFollowingRedirects(DIRECT_DOWNLOAD_URL, tmp)
            if (tmp.length() < 1_000_000L) {
                // A real yt-dlp zipapp is several MB; anything tiny means we saved an
                // error page instead of the binary.
                throw IOException("downloaded file looks truncated/invalid")
            }
            verifyZipMagic(tmp)
            if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
            // Stage next to the target and swap with an atomic rename, so a
            // yt-dlp process that is starting concurrently never observes a
            // missing or half-written binary.
            val staging = File(ytdlpDir, YoutubeDL.ytdlpBin + ".new")
            tmp.copyTo(staging, overwrite = true)
            staging.setExecutable(true, true)
            if (binary.exists()) binary.delete()
            if (!staging.renameTo(binary)) {
                // Same-filesystem rename should always work; fall back to a
                // plain copy for exotic devices.
                staging.copyTo(binary, overwrite = true)
                staging.delete()
                binary.setExecutable(true, true)
            }
        } finally {
            runCatching { tmp.delete() }
        }
    }

    /** The release asset is a python zipapp: it must start with the ZIP magic bytes "PK". */
    private fun verifyZipMagic(file: File) {
        file.inputStream().use { input ->
            val first = input.read()
            val second = input.read()
            if (first != 0x50 || second != 0x4B) {
                throw IOException("downloaded file is not a valid yt-dlp zipapp")
            }
        }
    }

    @Throws(IOException::class)
    private fun downloadFollowingRedirects(urlStr: String, dest: File, maxRedirects: Int = 5) {
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
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            return
        }
    }
}
