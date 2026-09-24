package com.devfahim00.duck.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App settings backed by SharedPreferences, exposed as Compose state so the
 * UI updates instantly when something changes.
 */
object Settings {

    /** Hard ceiling for connections per download (yt-dlp -N / aria2c -x -s). */
    const val MAX_THREADS = 32

    private lateinit var prefs: SharedPreferences

    /** Connections/fragments per download (yt-dlp -N or aria2c -x/-s). */
    var threads by mutableIntStateOf(8)
        private set

    /** How many downloads run at the same time. */
    var parallelDownloads by mutableIntStateOf(2)
        private set

    /** Turbo mode: use the bundled aria2c multi-connection downloader. */
    var turboAria2 by mutableStateOf(false)
        private set

    fun load(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences("duck_settings", Context.MODE_PRIVATE)
        threads = prefs.getInt("threads", 8).coerceIn(1, MAX_THREADS)
        parallelDownloads = prefs.getInt("parallel", 2)
        turboAria2 = prefs.getBoolean("turbo", false)
    }

    fun updateThreads(value: Int) {
        val capped = value.coerceIn(1, MAX_THREADS)
        threads = capped
        prefs.edit().putInt("threads", capped).apply()
    }

    fun updateParallel(value: Int) {
        parallelDownloads = value
        prefs.edit().putInt("parallel", value).apply()
    }

    fun updateTurbo(value: Boolean) {
        turboAria2 = value
        prefs.edit().putBoolean("turbo", value).apply()
    }
}
