package com.devfahim00.duck.ytdlp

import org.json.JSONObject

/** Video metadata returned by `ytdlp_bridge.fetch_formats` (replaces youtubedl-android's VideoInfo). */
data class VideoMeta(
    val title: String?,
    val fulltitle: String?,
    val webpageUrl: String?,
    val thumbnail: String?,
    val duration: Int,
    val uploader: String?,
    val formats: List<VideoFormat>
) {
    companion object {
        fun fromJson(o: JSONObject): VideoMeta {
            val arr = o.optJSONArray("formats")
            val formats = buildList<VideoFormat> {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(VideoFormat.fromJson(it)) }
                    }
                }
            }
            return VideoMeta(
                title = o.str("title"),
                fulltitle = o.str("fulltitle"),
                webpageUrl = o.str("webpage_url"),
                thumbnail = o.str("thumbnail"),
                duration = o.optDouble("duration", 0.0).let { if (it.isNaN()) 0 else it.toInt() },
                uploader = o.str("uploader"),
                formats = formats
            )
        }
    }
}

/** The handful of yt-dlp format fields the quality picker needs. */
data class VideoFormat(
    val height: Int,
    val fps: Int,
    val vcodec: String?,
    val acodec: String?,
    val fileSize: Long,
    val fileSizeApproximate: Long
) {
    companion object {
        fun fromJson(o: JSONObject) = VideoFormat(
            height = o.optDouble("height", 0.0).let { if (it.isNaN()) 0 else it.toInt() },
            fps = o.optDouble("fps", 0.0).let { if (it.isNaN()) 0 else it.toInt() },
            vcodec = o.str("vcodec"),
            acodec = o.str("acodec"),
            fileSize = o.optDouble("filesize", 0.0).toLong(),
            fileSizeApproximate = o.optDouble("filesize_approx", 0.0).toLong()
        )
    }
}

/** org.json turns JSON null into the string "null"; this returns a real null. */
internal fun JSONObject.str(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

/** One selectable row in the quality picker. */
data class FormatOption(
    val label: String,
    val detail: String,
    val formatSpec: String,
    val audioOnly: Boolean = false,
    val needsMerge: Boolean = false,
    val estBytes: Long? = null
)

/** Turns raw yt-dlp format metadata into a clean, human friendly list. */
object FormatOptions {

    fun build(info: VideoMeta): List<FormatOption> {
        val formats = info.formats
        val hasVideoStreams = formats.any { hasVideoStream(it) }
        val hasAudioOnlyStreams = formats.any { !hasVideoStream(it) && hasAudioStream(it) }

        val options = mutableListOf<FormatOption>()

        if (hasVideoStreams) {
            options += FormatOption(
                label = "Best quality",
                detail = "Best video + audio",
                formatSpec = "bv*+ba/b",
                needsMerge = true
            )

            val heights = formats
                .filter { hasVideoStream(it) && it.height > 0 }
                .map { it.height }
                .distinct()
                .sortedDescending()
                .take(8)

            for (h in heights) {
                val fps = formats
                    .filter { hasVideoStream(it) && it.height == h }
                    .maxOfOrNull { it.fps } ?: 0
                options += FormatOption(
                    label = qualityLabel(h, fps),
                    detail = "video + audio",
                    // Prefer a single combined stream when one exists at this
                    // height (HLS sites like xnxx report codec-less mp4 entries);
                    // otherwise fall back to separate video+audio streams.
                    formatSpec = "b[height<=$h]/bv*[height<=$h]+ba/b[height<=$h]",
                    needsMerge = true,
                    estBytes = estimateBytes(formats, h)
                )
            }
        }

        if (hasAudioOnlyStreams) {
            options += FormatOption(
                label = "Audio only",
                detail = "Best audio track",
                formatSpec = "ba/b",
                audioOnly = true
            )
        }

        if (options.isEmpty()) {
            // Very exotic extractor output - just let yt-dlp decide.
            options += FormatOption(
                label = "Default",
                detail = "Best available",
                formatSpec = "best"
            )
        }

        return options
    }

    private fun qualityLabel(height: Int, fps: Int): String = when {
        height >= 4320 -> "8K"
        height >= 2160 -> "4K UHD"
        height >= 1440 -> "2K QHD"
        else -> if (fps > 30) "${height}p ${fps}fps" else "${height}p"
    }

    private fun estimateBytes(formats: List<VideoFormat>, height: Int): Long? {
        val video = formats
            .filter { hasVideoStream(it) && it.height == height }
            .maxOfOrNull { maxOf(it.fileSize, it.fileSizeApproximate) } ?: 0L
        val audio = formats
            .filter { !hasVideoStream(it) && hasAudioStream(it) }
            .maxOfOrNull { maxOf(it.fileSize, it.fileSizeApproximate) } ?: 0L
        val total = video + audio
        return if (total > 0) total else null
    }

    /**
     * A format counts as a video stream when it either declares a video
     * codec or reports a resolution. HLS entries on sites like xnxx come
     * with `vcodec: unknown` but a real `height` - without this they would
     * be invisible to the quality picker.
     */
    private fun hasVideoStream(f: VideoFormat): Boolean =
        f.height > 0 || (!f.vcodec.isNullOrBlank() && f.vcodec != "none")

    private fun hasAudioStream(f: VideoFormat): Boolean =
        !f.acodec.isNullOrBlank() && f.acodec != "none"
}
