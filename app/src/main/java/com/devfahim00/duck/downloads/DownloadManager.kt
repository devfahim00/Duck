package com.devfahim00.duck.downloads

import android.content.Context
import android.media.MediaScannerConnection
import com.devfahim00.duck.util.FileUtils
import com.devfahim00.duck.util.Settings
import com.devfahim00.duck.ytdlp.FormatOption
import com.devfahim00.duck.ytdlp.YtDlpEngine
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Built-in download manager.
 *
 * - queue with a configurable number of simultaneous downloads
 * - live progress / speed / ETA straight from the yt-dlp process
 * - cancel (kills the process), retry, delete, clear finished
 * - history survives app restarts (JSON file in filesDir)
 */
object DownloadManager {

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val cancelledIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadHistory()
    }

    fun enqueue(url: String, title: String, option: FormatOption) {
        val item = DownloadItem(
            id = "dl_${System.currentTimeMillis()}_${(1000..9999).random()}",
            url = url,
            title = title,
            formatLabel = option.label,
            formatSpec = option.formatSpec,
            audioOnly = option.audioOnly,
            needsMerge = option.needsMerge
        )
        _downloads.update { current -> listOf(item) + current }
        persist()
        launchWorker(item.id)
    }

    fun cancel(id: String) {
        cancelledIds.add(id)
        val current = _downloads.value.find { it.id == id } ?: return
        if (current.status == DownloadStatus.QUEUED) {
            // not started yet - just mark it cancelled, the worker will bail out
            updateItem(id) { it.copy(status = DownloadStatus.CANCELLED) }
            persist()
        } else {
            YoutubeDL.getInstance().destroyProcessById(id)
        }
    }

    fun retry(id: String) {
        val item = _downloads.value.find { it.id == id } ?: return
        cancelledIds.remove(id)
        updateItem(id) {
            it.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                speed = null,
                etaSeconds = -1L,
                filePath = null,
                error = null
            )
        }
        persist()
        launchWorker(item.id)
    }

    fun delete(id: String) {
        cancelledIds.add(id)
        YoutubeDL.getInstance().destroyProcessById(id)
        _downloads.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    fun clearFinished() {
        _downloads.update { current ->
            current.filter {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
            }
        }
        persist()
    }

    private fun launchWorker(id: String) {
        scope.launch {
            val semaphore = Semaphore(Settings.parallelDownloads)
            semaphore.withPermit { performDownload(id) }
        }
    }

    private suspend fun performDownload(id: String) {
        val context = appContext ?: return
        val item = _downloads.value.find { it.id == id } ?: return
        if (item.status != DownloadStatus.QUEUED) return

        val startedAt = System.currentTimeMillis()
        updateItem(id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0f) }

        try {
            YtDlpEngine.awaitInitialized(context)
            val dir = FileUtils.downloadDir(context)
            val request = YtDlpEngine.buildDownloadRequest(
                url = item.url,
                formatSpec = item.formatSpec,
                audioOnly = item.audioOnly,
                needsMerge = item.needsMerge,
                outputDir = dir,
                threads = Settings.threads,
                turbo = Settings.turboAria2
            )

            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request, id) { progress, eta, line ->
                updateItem(id) { current ->
                    current.copy(
                        progress = if (progress >= 0f) progress else current.progress,
                        etaSeconds = eta,
                        speed = parseSpeed(line) ?: current.speed
                    )
                }
            }

            val path = extractFilePath(response.out)
                ?: dir.listFiles { f -> f.lastModified() >= startedAt }
                    ?.maxOfOrNull { it }
                    ?.absolutePath

            updateItem(id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100f,
                    filePath = path ?: it.filePath,
                    speed = null,
                    etaSeconds = -1L
                )
            }
            path?.let { MediaScannerConnection.scanFile(context, arrayOf(it), null, null) }
        } catch (e: YoutubeDL.CanceledException) {
            updateItem(id) { it.copy(status = DownloadStatus.CANCELLED, speed = null) }
        } catch (e: Exception) {
            val wasCancelled = cancelledIds.remove(id)
            updateItem(id) { current ->
                if (wasCancelled) {
                    current.copy(status = DownloadStatus.CANCELLED, speed = null)
                } else {
                    current.copy(status = DownloadStatus.FAILED, error = cleanError(e.message), speed = null)
                }
            }
        } finally {
            persist()
        }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { current -> current.map { if (it.id == id) transform(it) else it } }
    }

    /** "[download]  45.3% of ~12.50MiB at 1.05MiB/s ETA 00:12" -> "1.05MiB/s" */
    private val speedPattern = Regex("""at\s+([\d.]+\s*[KMGT]?i?B/s)""")

    /** aria2c turbo mode line "[#a1b2c3 10MiB/20MiB(50%) CN:8 DL:2.3MiB ETA:8s]" -> "2.3MiB/s" */
    private val ariaSpeedPattern = Regex("""DL:([\d.]+[KMGT]?i?B)""")

    private fun parseSpeed(line: String): String? {
        speedPattern.find(line)?.let { return it.groupValues[1] }
        ariaSpeedPattern.find(line)?.let { return it.groupValues[1] + "/s" }
        return null
    }

    private fun extractFilePath(out: String): String? =
        out.lines().map { it.trim() }.lastOrNull { it.startsWith("/") }

    private fun cleanError(message: String?): String {
        val msg = message?.trim().orEmpty()
        return when {
            msg.isEmpty() -> "Download failed"
            msg.length > 250 -> msg.take(250) + "..."
            else -> msg
        }
    }

    private fun persist() {
        val context = appContext ?: return
        val snapshot = _downloads.value
        scope.launch(persistDispatcher) {
            try {
                val arr = JSONArray()
                snapshot.take(200).forEach { d ->
                    arr.put(
                        JSONObject()
                            .put("id", d.id)
                            .put("url", d.url)
                            .put("title", d.title)
                            .put("formatLabel", d.formatLabel)
                            .put("formatSpec", d.formatSpec)
                            .put("audioOnly", d.audioOnly)
                            .put("needsMerge", d.needsMerge)
                            .put("status", d.status.name)
                            .put("progress", d.progress.toDouble())
                            .put("filePath", d.filePath ?: "")
                            .put("error", d.error ?: "")
                            .put("addedAt", d.addedAt)
                    )
                }
                File(context.filesDir, "downloads_history.json").writeText(arr.toString())
            } catch (_: Exception) {
                // history is best-effort
            }
        }
    }

    private fun loadHistory() {
        val context = appContext ?: return
        try {
            val file = File(context.filesDir, "downloads_history.json")
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            val items = mutableListOf<DownloadItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val status = when (o.optString("status")) {
                    "COMPLETED" -> DownloadStatus.COMPLETED
                    "CANCELLED" -> DownloadStatus.CANCELLED
                    // QUEUED/DOWNLOADING could not survive a restart
                    else -> DownloadStatus.FAILED
                }
                items += DownloadItem(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    title = o.getString("title"),
                    formatLabel = o.getString("formatLabel"),
                    formatSpec = o.getString("formatSpec"),
                    audioOnly = o.getBoolean("audioOnly"),
                    needsMerge = o.getBoolean("needsMerge"),
                    status = status,
                    progress = o.optDouble("progress", 0.0).toFloat(),
                    filePath = o.optString("filePath").ifBlank { null },
                    error = o.optString("error").ifBlank {
                        if (status == DownloadStatus.FAILED) "Interrupted" else null
                    },
                    addedAt = o.optLong("addedAt", System.currentTimeMillis())
                )
            }
            _downloads.value = items
        } catch (_: Exception) {
            // corrupted history - start fresh
        }
    }
}
