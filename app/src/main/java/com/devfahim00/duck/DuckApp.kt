package com.devfahim00.duck

import android.app.Application
import com.devfahim00.duck.downloads.DownloadManager
import com.devfahim00.duck.util.Settings
import com.devfahim00.duck.ytdlp.YtDlpEngine

class DuckApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Settings.load(this)
        DownloadManager.initialize(this)
        // Extracts bundled python / yt-dlp / ffmpeg / aria2c on first launch
        // (runs in background, first fetch or download waits for it).
        YtDlpEngine.prewarm(this)
    }
}
