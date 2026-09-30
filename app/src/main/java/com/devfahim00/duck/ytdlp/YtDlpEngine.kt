package com.devfahim00.duck.ytdlp

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.devfahim00.duck.util.CookieStore
import com.devfahim00.duck.util.Settings
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** A failure reported by yt-dlp (already stripped of ANSI codes / "ERROR:"). */
class YtDlpException(message: String) : Exception(message)

/** One progress tick from a running download. Negative numbers mean "unknown". */
data class ProgressUpdate(
    val percent: Float?,
    val speed: String?,
    val etaSeconds: Long?,
    val merging: Boolean
)

sealed interface DownloadOutcome {
    data class Completed(val filePath: String?) : DownloadOutcome
    data object Cancelled : DownloadOutcome
    data class Failed(val message: String) : DownloadOutcome
}

/**
 * Receives progress from the Python side. Python calls [onProgress] from the
 * thread that is running the download, so implementations must be thread-safe.
 * Public + concrete on purpose: Chaquopy exposes it to Python by reflection.
 */
class ProgressSink(private val callback: (ProgressUpdate) -> Unit) {
    @Suppress("unused") // called from ytdlp_bridge.py
    fun onProgress(percent: Double, speedBps: Double, etaSeconds: Double, merging: Boolean) {
        callback(
            ProgressUpdate(
                percent = if (percent >= 0) percent.toFloat() else null,
                speed = if (speedBps > 0) formatSpeed(speedBps) else null,
                etaSeconds = if (etaSeconds >= 0) etaSeconds.toLong() else null,
                merging = merging
            )
        )
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        val units = arrayOf("B/s", "KiB/s", "MiB/s", "GiB/s")
        var value = bytesPerSecond
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.2f%s", value, units[unit])
    }
}

/**
 * Duck's download engine: yt-dlp running inside Chaquopy's CPython 3.13
 * (see app/src/main/python/ytdlp_bridge.py) so that curl_cffi can give
 * yt-dlp real browser TLS impersonation.
 *
 * This object is the ONLY place that knows about Chaquopy. It
 *  - starts Python once and tells it where ffmpeg / aria2c live (both still
 *    come from the youtubedl-android AARs - only their Python is gone),
 *  - activates the newest yt-dlp the updater has downloaded (release channel
 *    stable / nightly / master, see [YtDlpUpdater]),
 *  - fetches formats and runs downloads through the bridge.
 */
object YtDlpEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    /** Background bootstrap (init + engine activation + network refresh). */
    @Volatile
    private var bootstrapJob: Job? = null

    /** Kick off engine init and the daily network refresh. */
    fun prewarm(context: Context) {
        if (bootstrapJob != null) return
        bootstrapJob = scope.launch {
            runCatching { awaitInitialized(context) }
        }
        // Network refresh only after init is done, so it never delays the
        // first fetch or download.
        scope.launch {
            bootstrapJob?.join()
            runCatching { YtDlpUpdater.refreshLatest(context) }
        }
    }

    /**
     * Suspends until Python is running and configured. Safe to call from
     * anywhere, any number of times; the first caller does the work.
     */
    suspend fun awaitInitialized(context: Context) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            val appContext = context.applicationContext
            withContext(Dispatchers.IO) {
                // Extract ffmpeg / aria2c support libs (no Python from that AAR is used).
                FFmpeg.getInstance().init(appContext)
                Aria2c.getInstance().init(appContext)

                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(appContext))
                }
                val nativeDir = appContext.applicationInfo.nativeLibraryDir
                val packages = File(File(appContext.noBackupFilesDir, "youtubedl-android"), "packages")
                bridge().callAttr(
                    "configure",
                    File(nativeDir, "libffmpeg.so").absolutePath,
                    File(packages, "ffmpeg/usr/lib").absolutePath,
                    File(nativeDir, "libaria2c.so").absolutePath,
                    File(packages, "aria2c/usr/lib").absolutePath
                )
                // Switch to the last downloaded yt-dlp (or keep the bundled one).
                YtDlpUpdater.activateSaved(appContext)
            }
            initialized = true
        }
    }

    /**
     * Suspends until the engine is ready. Normally instant; on a fresh
     * install it waits (up to a minute) for the background bootstrap, which
     * only does local work, so it works offline too.
     */
    suspend fun awaitReady(context: Context) {
        val job = bootstrapJob
        if (job != null && !job.isCompleted) {
            withTimeoutOrNull(60_000L) { job.join() }
        }
        awaitInitialized(context)
        // If an update finished while downloads were running it could not be
        // activated then (yt-dlp modules must not change under a live
        // download). This is the next safe moment. Off the main thread: a
        // real switch re-imports yt-dlp, which takes a moment.
        withContext(Dispatchers.IO) {
            runCatching { YtDlpUpdater.activateSaved(context.applicationContext) }
        }
    }

    // ------------------------------------------------------------ bridge access

    private fun bridge(): PyObject = Python.getInstance().getModule("ytdlp_bridge")

    /** Result of asking Python to switch yt-dlp. */
    data class EngineSwitch(val ok: Boolean, val busy: Boolean, val version: String?, val error: String?)

    /** Activates the yt-dlp zipapp at [path]; "" returns to the bundled pip copy. Blocking. */
    internal fun useEngine(path: String): EngineSwitch {
        val json = JSONObject(bridge().callAttr("use_engine", path).toString())
        return EngineSwitch(
            ok = json.optBoolean("ok", false),
            busy = json.optBoolean("busy", false),
            version = json.str("version"),
            error = json.str("error")
        )
    }

    /** Version of the yt-dlp that is active right now. Blocking. */
    internal fun activeVersion(): String? =
        bridge().callAttr("engine_version").toString().trim().takeIf { it.isNotEmpty() }

    /** JSON from `engine_diagnostics()`: curl_cffi / impersonation targets / versions. */
    suspend fun diagnostics(context: Context): String {
        awaitReady(context)
        return withContext(Dispatchers.IO) { bridge().callAttr("engine_diagnostics").toString() }
    }

    // -------------------------------------------------------------------- fetch

    /** Fetches video metadata + builds the list of selectable quality options. */
    suspend fun fetchFormats(context: Context, url: String): Pair<VideoMeta, List<FormatOption>> {
        awaitReady(context)
        return withContext(Dispatchers.IO) {
            val json = JSONObject(
                bridge().callAttr("fetch_formats", url, buildOptions(context, url).toString()).toString()
            )
            if (json.has("error") && !json.isNull("error")) {
                throw YtDlpException(json.optString("error"))
            }
            val meta = VideoMeta.fromJson(json)
            meta to FormatOptions.build(meta)
        }
    }

    // ----------------------------------------------------------------- download

    /**
     * Runs one download to completion (or cancel / failure). Suspends on an IO
     * thread; [onProgress] is called from that thread.
     *
     * Multi-threading:
     *  - turbo (aria2c): segmented multi-connection HTTP downloader (-x/-s connections)
     *  - default: yt-dlp native concurrent fragment downloads (-N)
     */
    suspend fun download(
        context: Context,
        id: String,
        url: String,
        formatSpec: String,
        audioOnly: Boolean,
        needsMerge: Boolean,
        outputDir: File,
        threads: Int,
        turbo: Boolean,
        onProgress: (ProgressUpdate) -> Unit
    ): DownloadOutcome {
        awaitReady(context)
        return withContext(Dispatchers.IO) {
            val json = JSONObject(
                bridge().callAttr(
                    "start_download",
                    id,
                    url,
                    formatSpec,
                    outputDir.absolutePath,
                    audioOnly,
                    needsMerge,
                    threads,
                    turbo,
                    buildOptions(context, url).toString(),
                    ProgressSink(onProgress)
                ).toString()
            )
            when (json.optString("status")) {
                "completed" -> DownloadOutcome.Completed(json.str("filepath"))
                "cancelled" -> DownloadOutcome.Cancelled
                else -> DownloadOutcome.Failed(json.str("message") ?: "Download failed")
            }
        }
    }

    /** Asks a running download to stop. Cooperative; returns immediately. */
    fun cancel(id: String) {
        if (!Python.isStarted()) return
        runCatching { bridge().callAttr("cancel", id) }
    }

    // ------------------------------------------------------------------ options

    /**
     * Request options shared by every fetch/download:
     *
     *  1. Accept-Language: another header every real browser sends on every
     *     request; some WAFs flag requests missing it.
     *  2. Referer: defaults to the video page's own origin. A lot of the
     *     "works in Seal / Termux but 403s in Duck" reports are hotlink-
     *     protected CDNs that check Referer + User-Agent together and reject a
     *     request with no Referer at all. Extractors that set their own
     *     Referer per request still win over this global default.
     *  3. User-Agent: NOT forced any more. yt-dlp's own default is a current
     *     Chrome UA, and - the important part - when a request is impersonated
     *     (curl_cffi) yt-dlp drops any header that equals its default so the
     *     browser fingerprint's own User-Agent is sent. A custom Android UA
     *     would contradict the Chrome TLS fingerprint, which is exactly what
     *     anti-bot systems detect. The one exception: when cookies were
     *     harvested from the built-in browser, that browser's UA is sent,
     *     because such sessions are often bound to it.
     *  4. cookie_file: for sites that only serve videos to logged-in browsers
     *     (Instagram, Facebook, age-restricted YouTube...). Imported via the
     *     built-in browser (Settings > Cookies) or a cookies.txt file.
     *  5. cache_dir: a real cache lets yt-dlp reuse solved YouTube player
     *     challenges = faster extraction and far fewer "confirm you're not a
     *     bot" walls.
     */
    private fun buildOptions(context: Context, url: String): JSONObject {
        val appContext = context.applicationContext
        val headers = JSONObject().put("Accept-Language", "en-US,en;q=0.9")

        if (Settings.cookiesEnabled) {
            CookieStore.userAgent(appContext)?.takeIf { it.isNotBlank() }?.let {
                headers.put("User-Agent", it)
            }
        }
        deriveOrigin(url)?.let { headers.put("Referer", it) }

        val options = JSONObject()
            .put("headers", headers)
            .put("impersonate_all", Settings.impersonateAll)

        runCatching {
            val cacheDir = File(appContext.cacheDir, "yt-dlp-cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            options.put("cache_dir", cacheDir.absolutePath)
        }
        if (Settings.cookiesEnabled) {
            val cookiesFile = CookieStore.file(appContext)
            if (cookiesFile.exists()) options.put("cookie_file", cookiesFile.absolutePath)
        }
        return options
    }

    /** "https://example.com/watch?v=123" -> "https://example.com/" */
    private fun deriveOrigin(url: String): String? = runCatching {
        val uri = java.net.URI(url)
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
        "$scheme://$host/"
    }.getOrNull()
}
