package com.devfahim00.duck.ytdlp

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wraps YoutubeDL.getInstance().updateYoutubeDL().
 *
 * Why the plain library call fails so often for us: youtubedl-android's updater hits
 * https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest directly and unauthenticated
 * to check + resolve the download URL. GitHub allows only 60 anonymous requests/hour
 * *per source IP*. On carrier mobile networks (very common for our BD users, heavy CGNAT)
 * huge numbers of subscribers share one public IP, so that quota is exhausted almost
 * immediately and the API responds 403. The library just wraps that as
 * "failed to update youtube-dl" with no actionable detail.
 *
 * Fallback: fetch the binary straight from GitHub's "latest release" redirect
 * (github.com/.../releases/latest/download/yt-dlp). That endpoint is a plain CDN redirect,
 * NOT the rate-limited REST API, so it keeps working even when api.github.com is throttled.
 * We drop the result into the exact directory the bundled library already expects, so the
 * rest of the app (YtDlpEngine, version()) keeps working unmodified.
 */
object YtDlpUpdater {

    private const val DIRECT_DOWNLOAD_URL =
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    /** Call from Dispatchers.IO. */
    fun update(context: Context): Result {
        // 1) Try the library's own updater first: it's a no-op when already current and
        // records the proper version tag when it succeeds.
        val primary = runCatching { YoutubeDL.getInstance().updateYoutubeDL(context) }
        primary.getOrNull()?.let {
            return Result.Success(
                when (it) {
                    YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated to the latest release"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date"
                }
            )
        }

        val primaryError = primary.exceptionOrNull()

        // 2) Primary path failed — most commonly the api.github.com rate limit above.
        // Fall back to a direct, non-API download of the latest release asset.
        return try {
            downloadDirect(context)
            Result.Success("yt-dlp updated to the latest release (direct download)")
        } catch (fallbackError: Exception) {
            val reason = primaryError?.message ?: fallbackError.message ?: "unknown error"
            Result.Failure("Update failed: ${reason.take(160)}")
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
            FileUtils.copyFile(tmp, binary)
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
