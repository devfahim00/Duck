package com.devfahim00.duck.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Netscape-format cookies.txt support (same idea as the Seal app): some sites
 * only serve videos to logged-in browsers - Instagram, Facebook, private or
 * age-restricted YouTube, "Sign in to confirm you're not a bot" checks and so
 * on. The user exports a cookies.txt from their browser, imports it here, and
 * every yt-dlp request (fetch + download) is run with `--cookies <file>`.
 *
 * The file lives in noBackupFilesDir (app-private, stable path). Importing
 * copies the picked SAF document into that location so no persisted URI
 * permission is needed.
 */
object CookieStore {

    private const val COOKIES_NAME = "cookies.txt"
    private const val STAGING_NAME = "cookies.txt.new"

    fun file(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, COOKIES_NAME)

    fun exists(context: Context): Boolean =
        file(context).let { it.exists() && it.length() > 0 }

    /**
     * Copies the user-picked cookies.txt into app-private storage.
     *
     * @return null on success, otherwise a human readable error to show in
     * the settings sheet.
     */
    fun import(context: Context, uri: Uri): String? {
        val appContext = context.applicationContext
        val target = file(appContext)
        val staging = File(appContext.noBackupFilesDir, STAGING_NAME)
        return try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: return "Could not open the selected file"
            input.use { src ->
                staging.outputStream().use { out -> src.copyTo(out) }
            }
            if (staging.length() == 0L) {
                staging.delete()
                return "The selected file is empty"
            }
            if (!looksLikeCookiesFile(staging)) {
                staging.delete()
                return "That does not look like a Netscape cookies.txt file - " +
                    "export cookies from your browser again (cookie-export extensions produce this format)"
            }
            if (target.exists()) target.delete()
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }
            null
        } catch (e: Exception) {
            runCatching { staging.delete() }
            "Import failed: ${e.message?.take(120) ?: "unknown error"}"
        }
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            file(appContext).delete()
            File(appContext.noBackupFilesDir, STAGING_NAME).delete()
        }
    }

    /**
     * Light sanity check on the first lines of the file: Netscape cookies.txt
     * contains comment lines starting with '#' and/or tab-separated cookie
     * rows (domain, includeSubdomains, path, secure, expiry, name, value).
     * This catches accidentally picking a JSON/HTML file, which would fail
     * silently inside yt-dlp much later and confuse the user.
     */
    private fun looksLikeCookiesFile(f: File): Boolean = try {
        val head = mutableListOf<String>()
        f.inputStream().bufferedReader().use { reader ->
            var line = reader.readLine()
            while (line != null && head.size < 50) {
                head.add(line)
                line = reader.readLine()
            }
        }
        head.any { line ->
            line.startsWith("#") || line.split("\t").size >= 6
        }
    } catch (e: Exception) {
        // Unreadable head: let yt-dlp decide later instead of blocking the import.
        true
    }
}
