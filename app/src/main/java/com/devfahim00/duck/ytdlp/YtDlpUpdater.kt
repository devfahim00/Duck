package com.devfahim00.duck.ytdlp

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wraps YoutubeDL.getInstance().updateYoutubeDL().
 *
 * The app is pinned to a specific yt-dlp release ([PINNED_VERSION]) rather than
 * whatever GitHub reports as "latest": a known-good version avoids surprise
 * regressions and lets us test one exact build. update() therefore does NOT call
 * the library's own "latest" updater (which also hits the rate-limited
 * api.github.com/.../releases/latest endpoint — see below) and instead always
 * (re)installs [PINNED_VERSION] via a direct, non-API download.
 *
 * Why not the library's own updater: youtubedl-android's updater hits
 * https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest directly and
 * unauthenticated. GitHub allows only 60 anonymous requests/hour *per source IP*.
 * On carrier mobile networks (very common for our BD users, heavy CGNAT) huge
 * numbers of subscribers share one public IP, so that quota is exhausted almost
 * immediately and the call fails with an opaque "failed to update youtube-dl".
 *
 * Instead we fetch the pinned tag's asset directly
 * (github.com/.../releases/download/$PINNED_VERSION/yt-dlp), a plain CDN redirect,
 * NOT the rate-limited REST API, so it keeps working even when api.github.com is
 * throttled. We drop the result into the exact directory the bundled library
 * already expects, so the rest of the app (YtDlpEngine, version()) keeps working
 * unmodified.
 */
object YtDlpUpdater {

    /** yt-dlp release this app is pinned to. Bump this string to move to a new version. */
    const val PINNED_VERSION = "2026.08.19"

    private const val DIRECT_DOWNLOAD_URL =
        "https://github.com/yt-dlp/yt-dlp/releases/download/$PINNED_VERSION/yt-dlp"

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    /** Call from Dispatchers.IO. Always (re)installs [PINNED_VERSION]. */
    fun update(context: Context): Result {
        return try {
            downloadDirect(context)
            Result.Success("yt-dlp pinned to $PINNED_VERSION")
        } catch (error: Exception) {
            Result.Failure("Update failed: ${error.message?.take(160) ?: "unknown error"}")
        }
    }

    private fun downloadDirect(context: Context) {
        val ytdlpDir = File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName)
        val binary = File(ytdlpDir, YoutubeDL.ytdlpBin)
        val tmp = File.createTempFile("yt-dlp", null, context.cacheDir)
        try {
            downloadFollowingRedirects(DIRECT_DOWNLOAD_URL, tmp)
            if (tmp.length() < 1_000_000L) {
                // A real yt-dlp zipapp is several MB; anything tiny means we saved an
                // error page instead of the binary.
                throw IOException("downloaded file looks truncated/invalid")
            }
            if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
            if (binary.exists()) binary.delete()
            tmp.copyTo(binary, overwrite = true)
            binary.setExecutable(true)
        } finally {
            tmp.delete()
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
