package com.devfahim00.duck.ytdlp

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper around youtubedl-android.
 * Takes care of one-time initialization (python/yt-dlp/ffmpeg/aria2c extraction)
 * and builds the yt-dlp commands used by the app.
 */
object YtDlpEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    /** Kick off engine init in the background right after app start. */
    fun prewarm(context: Context) {
        scope.launch {
            runCatching { awaitInitialized(context) }
        }
    }

    /**
     * Suspends until the engine is ready. Safe to call from anywhere, any number
     * of times; the first caller performs the extraction, everyone else waits.
     */
    suspend fun awaitInitialized(context: Context) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            val appContext = context.applicationContext
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                Aria2c.getInstance().init(appContext)
            }
            initialized = true
        }
    }

    /**
     * Fetches video metadata + builds the list of selectable quality options.
     */
    suspend fun fetchFormats(context: Context, url: String): Pair<VideoInfo, List<FormatOption>> {
        awaitInitialized(context)
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", "15")
            request.addOption("--retries", "2")
            val info = YoutubeDL.getInstance().getInfo(request)
            info to FormatOptions.build(info)
        }
    }

    /**
     * Builds the yt-dlp download command for a selected quality.
     *
     * Multi-threading:
     *  - turbo (aria2c): segmented multi-connection HTTP downloader (-x/-s connections)
     *  - default: yt-dlp native concurrent fragment downloads (-N)
     */
    fun buildDownloadRequest(
        url: String,
        formatSpec: String,
        audioOnly: Boolean,
        needsMerge: Boolean,
        outputDir: File,
        threads: Int,
        turbo: Boolean
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--newline")
        // CRITICAL: --print (below) implies --quiet, which implies --no-progress in
        // yt-dlp -> NO progress lines on stdout at all (progress UI stays at 0%
        // until the download finishes). --progress explicitly re-enables the
        // progress output, and combined with --newline each update arrives as its
        // own stdout line that the youtubedl-android callback can parse.
        request.addOption("--progress")
        request.addOption("-f", formatSpec)
        request.addOption("-o", outputDir.absolutePath + "/%(title)s [%(id)s].%(ext)s")
        // yt-dlp prints the final file path as the last stdout line
        request.addOption("--print", "after_move:filepath")
        request.addOption("--socket-timeout", "15")

        if (needsMerge && !audioOnly) {
            request.addOption("--merge-output-format", "mkv")
        }

        if (turbo) {
            // youtubedl-android ships aria2c as libaria2c.so; the library injects
            // the extra downloader args (summary interval, CA cert) automatically.
            request.addOption("--downloader", "libaria2c.so")
            request.addOption(
                "--external-downloader-args",
                "aria2c:-x $threads -s $threads -k 1M"
            )
        } else {
            request.addOption("-N", threads)
        }

        return request
    }
}
