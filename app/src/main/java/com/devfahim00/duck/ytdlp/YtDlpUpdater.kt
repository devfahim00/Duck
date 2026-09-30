package com.devfahim00.duck.ytdlp

import android.content.Context
import com.devfahim00.duck.util.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the yt-dlp inside the Chaquopy runtime current.
 *
 * Two layers, in order of trust:
 *
 *  1. **Bundled yt-dlp** - the exact version pip-installed into the APK by
 *     Chaquopy (see `chaquopy { pip { install("yt-dlp==...") } }` in
 *     app/build.gradle.kts). Always present, works offline on first launch.
 *
 *  2. **Downloaded release** - a yt-dlp zipapp from the selected channel
 *     ([UpdateChannel]: stable / nightly / master). Python imports a zipapp
 *     directly from sys.path, so "installing" an update is: download,
 *     validate, atomically move into place, tell the bridge to switch. The
 *     newest of the two wins on every launch.
 *
 * All network access goes through GitHub's release CDN redirects
 * (`releases/latest/download/yt-dlp`, `releases/latest`), never the
 * rate-limited api.github.com REST endpoints - carrier CGNAT burns through
 * its 60 requests/hour instantly, which is what broke the very first updater.
 *
 * A downloaded engine that fails to import is deleted and the bundled one
 * keeps working, so an update can never leave the app without an engine.
 */
object YtDlpUpdater {

    /** Keep in sync with `install("yt-dlp==...")` in app/build.gradle.kts. */
    const val BUNDLED_VERSION = "2026.08.19"

    private const val UNKNOWN_VERSION = "unknown"
    private const val PREFS_NAME = "duck_settings"
    private const val PREF_ENGINE_PATH = "ytdlp_engine_path"
    private const val PREF_ENGINE_VERSION = "ytdlp_engine_version"
    private const val PREF_ENGINE_CHANNEL = "ytdlp_engine_channel"
    private const val PREF_LAST_NET_ATTEMPT = "ytdlp_net_update_last_attempt"

    /** How long to wait before the background refresh tries again. */
    private const val NET_RETRY_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    // ---------------------------------------------------------------- activate

    /**
     * Makes Python use the saved downloaded engine, if there is one that is
     * newer than the bundled copy. Cheap and idempotent: Python short-circuits
     * when the requested engine is already active. Called on init and before
     * every fetch/download so an update that arrived mid-download gets
     * applied at the next safe moment.
     */
    fun activateSaved(context: Context) {
        val prefs = prefs(context)
        val path = prefs.getString(PREF_ENGINE_PATH, null)
        val savedVersion = prefs.getString(PREF_ENGINE_VERSION, null)

        if (path == null || !File(path).exists()) {
            if (path != null) clearSaved(context)
            YtDlpEngine.useEngine("")
            return
        }
        // An app update can ship a newer bundled yt-dlp than the one we
        // downloaded earlier; never go backwards.
        if (savedVersion != null && savedVersion != UNKNOWN_VERSION &&
            compareVersions(savedVersion, BUNDLED_VERSION) <= 0
        ) {
            clearSaved(context)
            YtDlpEngine.useEngine("")
            return
        }
        val result = YtDlpEngine.useEngine(path)
        if (!result.ok && !result.busy) {
            // Corrupt download: Python already fell back to the bundled copy.
            clearSaved(context)
        }
    }

    // ---------------------------------------------------------------- refresh

    /**
     * BACKGROUND REFRESH - quietly moves to the newest release of the selected
     * channel at most once per day. Best-effort by design: offline devices
     * simply stay on whatever engine they have, which always works.
     */
    suspend fun refreshLatest(context: Context) {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val lastAttempt = prefs.getLong(PREF_LAST_NET_ATTEMPT, 0L)
        if (System.currentTimeMillis() - lastAttempt < NET_RETRY_COOLDOWN_MS) return
        markNetAttempt(appContext)
        runCatching { install(appContext, Settings.updateChannel, tag = null) }
    }

    /**
     * MANUAL update (Settings > Engine & Updates). Follows the selected
     * channel. Pass [tag] to install a specific release instead of the newest
     * (the `--update-to CHANNEL@TAG` equivalent).
     */
    suspend fun update(context: Context, tag: String? = null): Result {
        val appContext = context.applicationContext
        val channel = Settings.updateChannel
        return try {
            markNetAttempt(appContext)
            val outcome = install(appContext, channel, tag)
            Result.Success(outcome)
        } catch (error: Exception) {
            // Never leave the user stranded: whatever engine was active still is.
            Result.Failure(
                "Update failed (${error.message?.take(120) ?: "network error"}) - " +
                    "still on ${currentVersion(appContext) ?: BUNDLED_VERSION}"
            )
        }
    }

    /** The REAL version of the yt-dlp active in Python right now. */
    suspend fun currentVersion(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            YtDlpEngine.awaitInitialized(context)
            YtDlpEngine.activeVersion()
        }.getOrNull()
    }

    // ---------------------------------------------------------------- install

    /** Returns a human-readable outcome; throws on failure. */
    private suspend fun install(appContext: Context, channel: UpdateChannel, tag: String?): String =
        withContext(Dispatchers.IO) {
            YtDlpEngine.awaitInitialized(appContext)
            val installed = YtDlpEngine.activeVersion()

            // Skip the 3 MB download when we already run the newest release.
            val targetTag = tag ?: runCatching { latestTag(channel) }.getOrNull()
            if (targetTag != null && installed != null && targetTag == installed) {
                return@withContext "Already on the latest ${channel.label.lowercase()} release ($installed)"
            }

            val url = if (tag != null) channel.assetUrlForTag(tag) else channel.latestAssetUrl
            val tmp = File.createTempFile("yt-dlp", null, appContext.cacheDir)
            try {
                downloadFollowingRedirects(url, tmp)
                if (tmp.length() < 1_000_000L) {
                    // A real yt-dlp zipapp is several MB; anything tiny means
                    // we saved an error page instead of the binary.
                    throw IOException("downloaded file looks truncated/invalid")
                }
                if (!looksLikeYtDlpZipapp(tmp)) {
                    throw IOException("downloaded file is not a valid yt-dlp zipapp")
                }

                val dir = engineDir(appContext)
                if (!dir.exists()) dir.mkdirs()
                // Unique name per install: Python caches zip directories by
                // path, so reusing a path could serve the previous contents.
                val staged = File(dir, "yt-dlp-${System.currentTimeMillis()}.zip")
                if (!tmp.renameTo(staged)) {
                    tmp.copyTo(staged, overwrite = true)
                }

                val result = YtDlpEngine.useEngine(staged.absolutePath)
                if (!result.ok && !result.busy) {
                    runCatching { staged.delete() }
                    throw IOException(result.error ?: "new engine failed to load")
                }

                // Record it. If Python was busy with a running download, the
                // switch happens at the next fetch/download via activateSaved().
                val version = result.version ?: targetTag ?: UNKNOWN_VERSION
                prefs(appContext).edit()
                    .putString(PREF_ENGINE_PATH, staged.absolutePath)
                    .putString(PREF_ENGINE_VERSION, version)
                    .putString(PREF_ENGINE_CHANNEL, channel.id)
                    .apply()
                pruneOldEngines(dir, keep = staged)

                if (result.busy) {
                    "Downloaded yt-dlp${targetTag?.let { " $it" }.orEmpty()} - " +
                        "it activates when the current downloads finish"
                } else {
                    "yt-dlp updated to $version (${channel.label.lowercase()})"
                }
            } finally {
                runCatching { tmp.delete() }
            }
        }

    /** Newest release tag of a channel via the `releases/latest` redirect. */
    private fun latestTag(channel: UpdateChannel): String? {
        val conn = URL(channel.latestTagUrl).openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Duck-Android-App")
            conn.connect()
            conn.getHeaderField("Location")?.substringAfter("/tag/", "")?.takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    private fun clearSaved(context: Context) {
        val prefs = prefs(context)
        val path = prefs.getString(PREF_ENGINE_PATH, null)
        prefs.edit()
            .remove(PREF_ENGINE_PATH)
            .remove(PREF_ENGINE_VERSION)
            .remove(PREF_ENGINE_CHANNEL)
            .apply()
        if (path != null) runCatching { File(path).delete() }
    }

    private fun pruneOldEngines(dir: File, keep: File) {
        dir.listFiles()?.forEach { f ->
            if (f != keep && f.name.startsWith("yt-dlp-") && f.name.endsWith(".zip")) {
                runCatching { f.delete() }
            }
        }
    }

    /**
     * yt-dlp release assets are python zipapps: `#!/usr/bin/env python3`
     * shebang followed by ZIP data ("PK\u0003\u0004") within the first line.
     * Accept both bare zips and shebang-prefixed zipapps.
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
                String(head, 0, read, Charsets.ISO_8859_1).contains("PK\u0003\u0004")
            }
        } catch (_: Exception) {
            false
        }
    }

    /** yt-dlp versions are dates: `2026.08.19` or `2026.09.27.232945`. */
    internal fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toLongOrNull() ?: 0L }
        val pb = b.split('.').map { it.toLongOrNull() ?: 0L }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0L }
            val y = pb.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun engineDir(appContext: Context): File = File(appContext.filesDir, "ytdlp-engine")

    private fun markNetAttempt(appContext: Context) {
        prefs(appContext).edit()
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
