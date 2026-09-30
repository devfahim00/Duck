package com.devfahim00.duck.downloads

import android.content.Context
import android.media.MediaScannerConnection
import com.devfahim00.duck.util.FileUtils
import com.devfahim00.duck.util.Settings
import com.devfahim00.duck.ytdlp.DownloadOutcome
import com.devfahim00.duck.ytdlp.FormatOption
import com.devfahim00.duck.ytdlp.YtDlpEngine
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
            YtDlpEngine.cancel(id)
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
                error = null,
                merging = false
            )
        }
        persist()
        launchWorker(item.id)
    }

    fun delete(id: String) {
        cancelledIds.add(id)
        YtDlpEngine.cancel(id)
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
            YtDlpEngine.awaitReady(context)
            val dir = FileUtils.downloadDir(context)

            val outcome = YtDlpEngine.download(
                context = context,
                id = id,
                url = item.url,
                formatSpec = item.formatSpec,
                audioOnly = item.audioOnly,
                needsMerge = item.needsMerge,
                outputDir = dir,
                threads = Settings.threads,
                turbo = Settings.turboAria2
            ) { progress ->
                updateItem(id) { current ->
                    current.copy(
                        progress = progress.percent ?: current.progress,
                        etaSeconds = progress.etaSeconds ?: current.etaSeconds,
                        speed = progress.speed ?: current.speed,
                        merging = current.merging || progress.merging
                    )
                }
            }

            when (outcome) {
                is DownloadOutcome.Completed -> {
                    val path = outcome.filePath?.takeIf { File(it).exists() }
                        ?: dir.listFiles { f -> f.lastModified() >= startedAt }
                            ?.maxOfOrNull { it }
                            ?.absolutePath

                    updateItem(id) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 100f,
                            filePath = path ?: it.filePath,
                            speed = null,
                            etaSeconds = -1L,
                            merging = false
                        )
                    }
                    path?.let { MediaScannerConnection.scanFile(context, arrayOf(it), null, null) }
                }

                is DownloadOutcome.Cancelled -> {
                    cancelledIds.remove(id)
                    updateItem(id) { it.copy(status = DownloadStatus.CANCELLED, speed = null, merging = false) }
                }

                is DownloadOutcome.Failed -> {
                    val wasCancelled = cancelledIds.remove(id)
                    updateItem(id) { current ->
                        if (wasCancelled) {
                            current.copy(status = DownloadStatus.CANCELLED, speed = null, merging = false)
                        } else {
                            current.copy(
                                status = DownloadStatus.FAILED,
                                error = cleanError(outcome.message),
                                speed = null,
                                merging = false
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val wasCancelled = cancelledIds.remove(id)
            updateItem(id) { current ->
                if (wasCancelled) {
                    current.copy(status = DownloadStatus.CANCELLED, speed = null, merging = false)
                } else {
                    current.copy(status = DownloadStatus.FAILED, error = cleanError(e.message), speed = null, merging = false)
                }
            }
        } finally {
            persist()
        }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { current -> current.map { if (it.id == id) transform(it) else it } }
    }

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
                            .put("merging", false)
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
