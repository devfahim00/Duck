package com.devfahim00.duck.ytdlp

import android.content.Context
import com.devfahim00.duck.util.CookieStore
import com.devfahim00.duck.util.Settings
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Thin wrapper around youtubedl-android.
 * Takes care of one-time initialization (python/yt-dlp/ffmpeg/aria2c extraction),
 * keeps the yt-dlp binary current (see [YtDlpUpdater.ensureLatest]) and builds
 * the yt-dlp commands used by the app.
 */
object YtDlpEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    /**
     * One-shot background job that (a) extracts the runtime and (b) brings the
     * yt-dlp binary up to [YtDlpUpdater.PINNED_VERSION]. Fetches and downloads
     * wait for it (bounded) via [awaitReady] so they run with a *current*
     * yt-dlp instead of the months-old one bundled inside the app.
     */
    @Volatile
    private var bootstrapJob: Job? = null

    /** Kick off engine init + auto-update in the background right after app start. */
    fun prewarm(context: Context) {
        if (bootstrapJob != null) return
        bootstrapJob = scope.launch {
            runCatching { awaitInitialized(context) }
            runCatching { YtDlpUpdater.ensureLatest(context) }
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
     * Suspends until the engine is ready AND the first-launch auto-update has
     * finished (or 60s passed). Waiting is strictly better than proceeding:
     * the stale bundled yt-dlp fails on a large share of sites (the whole
     * "works in Termux but not in the app" problem), so a fetch that jumps the
     * gun would just produce a guaranteed error for the user.
     */
    suspend fun awaitReady(context: Context) {
        awaitInitialized(context)
        val job = bootstrapJob
        if (job != null && !job.isCompleted) {
            withTimeoutOrNull(60_000L) { job.join() }
        }
    }

    /**
     * Fetches video metadata + builds the list of selectable quality options.
     */
    suspend fun fetchFormats(context: Context, url: String): Pair<VideoInfo, List<FormatOption>> {
        awaitReady(context)
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", "20")
            request.addOption("--retries", "3")
            request.addOption("--extractor-retries", "2")
            applySharedOptions(context, request)
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
        context: Context,
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
        request.addOption("--socket-timeout", "20")
        request.addOption("--retries", "3")
        applySharedOptions(context, request)

        if (needsMerge && !audioOnly) {
            request.addOption("--merge-output-format", "mkv")
        }

        if (turbo) {
            // youtubedl-android ships aria2c as libaria2c.so.
            // BUG FIX: aria2c's own default --summary-interval is 60 seconds, so
            // without setting it explicitly the UI would sit at 0% / "Connecting"
            // for up to a full minute (or the whole download, if it finishes
            // sooner) with no progress lines at all, then jump straight to
            // completed once the process exited. Forcing summary-interval=1
            // makes aria2c print a fresh line every second so the progress bar
            // actually animates while the file is downloading.
            request.addOption("--downloader", "libaria2c.so")
            request.addOption(
                "--external-downloader-args",
                "aria2c:-x $threads -s $threads -k 1M --summary-interval=1"
            )
        } else {
            request.addOption("-N", threads)
        }

        return request
    }

    /**
     * Options shared by every fetch/download request, mirroring what makes
     * Termux / Seal work on sites the old build failed on:
     *
     *  1. --no-check-certificate: some servers (and some devices' CA stores)
     *     fail TLS verification and kill the download with
     *     CERTIFICATE_VERIFY_FAILED; Seal passes the same flag.
     *  2. --cache-dir: youtubedl-android forces --no-cache-dir unless the
     *     request carries its own --cache-dir. A real cache lets yt-dlp reuse
     *     solved YouTube player challenges = faster extraction and far fewer
     *     "confirm you're not a bot" walls; desktop/Termux yt-dlp caches by
     *     default.
     *  3. --cookies: for sites that only serve videos to logged-in browsers
     *     (Instagram, Facebook, age-restricted YouTube...). Same feature as
     *     Seal's cookie setting; imported in Settings > Cookies.
     */
    private fun applySharedOptions(context: Context, request: YoutubeDLRequest) {
        request.addOption("--no-check-certificate")

        runCatching {
            val cacheDir = File(context.applicationContext.cacheDir, "yt-dlp-cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            request.addOption("--cache-dir", cacheDir.absolutePath)
        }

        if (Settings.cookiesEnabled) {
            val cookiesFile = CookieStore.file(context)
            if (cookiesFile.exists()) {
                request.addOption("--cookies", cookiesFile.absolutePath)
            }
        }
    }
}
