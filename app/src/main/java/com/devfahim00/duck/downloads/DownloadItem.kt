package com.devfahim00.duck.downloads

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val formatLabel: String,
    val formatSpec: String,
    val audioOnly: Boolean = false,
    val needsMerge: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f, // 0..100
    val speed: String? = null,
    val etaSeconds: Long = -1L,
    val filePath: String? = null,
    val error: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
