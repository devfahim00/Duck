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
 * installs the pinned yt-dlp engine from APK resources (see
 * [YtDlpUpdater.ensurePinnedEngine]) and builds the yt-dlp commands used by
 * the app.
 */
object YtDlpEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    /**
     * One-shot background job that (a) extracts the runtime, (b) swaps the
     * stale binary bundled in the AAR for the pinned engine shipped in
     * the APK (local copy, works offline) and (c) refreshes to the newest
     * stable yt-dlp from the network. (a)+(b) are what fetches and
     * downloads wait for via [awaitReady]; (c) is best-effort background
     * work that the NEXT download picks up.
     */
    @Volatile
    private var bootstrapJob: Job? = null

    /** Kick off engine init + pinned engine install + network refresh. */
    fun prewarm(context: Context) {
        if (bootstrapJob != null) return
        bootstrapJob = scope.launch {
            runCatching { awaitInitialized(context) }
            runCatching { YtDlpUpdater.ensurePinnedEngine(context) }
        }
        // Network refresh only after the ready part is done - the running
        // python env is extracted and the pinned binary is in place.
        scope.launch {
            bootstrapJob?.join()
            runCatching { YtDlpUpdater.refreshLatest(context) }
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
     * Suspends until the engine is ready: runtime extracted AND the pinned
     * (known-good) yt-dlp binary installed. Both steps are local-only, so
     * this normally completes in a few seconds even on a fresh, offline
     * install - the stale AAR binary (the one with the
     * "I/O operation on closed file" urllib regression) is never used.
     */
    suspend fun awaitReady(context: Context) {
        val job = bootstrapJob
        if (job != null && !job.isCompleted) {
            withTimeoutOrNull(60_000L) { job.join() }
        }
        awaitInitialized(context)
        runCatching { YtDlpUpdater.ensurePinnedEngine(context) }
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
        request.addOption("--no-mtime")
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
     *     Seal's cookie setting; imported via the built-in browser (Settings
     *     > Cookies > Sign in with browser) or a cookies.txt file.
     *  4. --add-header User-Agent: when cookies were harvested from the
     *     built-in browser, send them with the same user-agent the browser
     *     used (exactly what Seal does) - many sites tie sessions to the UA.
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
                CookieStore.userAgent(context)?.takeIf { it.isNotBlank() }?.let { ua ->
                    request.addOption("--add-header", "User-Agent:$ua")
                }
            }
        }
    }
}
