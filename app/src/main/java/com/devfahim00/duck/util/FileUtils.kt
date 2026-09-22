package com.devfahim00.duck.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object FileUtils {

    /** All Files Access only exists on Android 11+ (API 30+). */
    fun hasAllFilesAccess(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    /**
     * Public Downloads/Duck when All Files Access is granted,
     * otherwise the app-scoped external folder (no permission needed).
     */
    @Suppress("DEPRECATION")
    fun downloadDir(context: Context): File {
        if (hasAllFilesAccess()) {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Duck"
            )
            if (!publicDir.exists()) publicDir.mkdirs()
            if (publicDir.canWrite()) return publicDir
        }
        val appDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Duck")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    fun storageModeText(context: Context): String =
        if (hasAllFilesAccess()) "Downloads/Duck (public)"
        else "App private folder (grant All Files Access to use Downloads/Duck)"

    fun effectiveLocationText(context: Context): String = downloadDir(context).absolutePath

    fun openFile(context: Context, path: String): Boolean =
        launchFileIntent(context, path, Intent.ACTION_VIEW)

    fun shareFile(context: Context, path: String): Boolean =
        launchFileIntent(context, path, Intent.ACTION_SEND)

    private fun launchFileIntent(context: Context, path: String, action: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(action)
            if (action == Intent.ACTION_SEND) {
                intent.type = mimeTypeFor(file.name)
                intent.putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                intent.setDataAndType(uri, mimeTypeFor(file.name))
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (action == Intent.ACTION_SEND) {
                context.startActivity(Intent.createChooser(intent, "Share via"))
            } else {
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mimeTypeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov", "qt" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "ts" -> "video/mp2t"
            "3gp" -> "video/3gpp"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus" -> "audio/opus"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/x-wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes}B" else String.format(Locale.ROOT, "%.1f%s", value, units[unit])
    }

    fun formatEta(seconds: Long): String {
        if (seconds < 0) return "--:--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", m, s)
        }
    }

    fun formatDuration(seconds: Long): String = formatEta(seconds)
}
