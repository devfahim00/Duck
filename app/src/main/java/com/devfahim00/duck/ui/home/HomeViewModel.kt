package com.devfahim00.duck.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devfahim00.duck.downloads.DownloadManager
import com.devfahim00.duck.ytdlp.FormatOption
import com.devfahim00.duck.ytdlp.YtDlpEngine
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FetchState {
    data object Idle : FetchState
    data object Loading : FetchState
    data class Ready(val info: VideoInfo, val options: List<FormatOption>) : FetchState
    data class Error(val message: String) : FetchState
}

class HomeViewModel(
    private val engine: YtDlpEngine,
    private val downloads: DownloadManager
) : ViewModel() {

    var url by mutableStateOf("")

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun onUrlChange(value: String) {
        url = value
        if (value.isBlank()) _fetchState.value = FetchState.Idle
    }

    fun fetch(context: Context) {
        val target = url.trim()
        if (target.isEmpty()) return
        _fetchState.value = FetchState.Loading
        viewModelScope.launch {
            try {
                val (info, options) = engine.fetchFormats(context, target)
                if (options.isEmpty()) {
                    _fetchState.value = FetchState.Error(
                        "No downloadable formats found for this link."
                    )
                } else {
                    _fetchState.value = FetchState.Ready(info, options)
                }
            } catch (e: Exception) {
                _fetchState.value = FetchState.Error(prettify(e.message))
            }
        }
    }

    fun startDownload(context: Context, option: FormatOption) {
        val state = _fetchState.value
        val info = (state as? FetchState.Ready)?.info ?: return
        val target = url.trim().ifBlank { info.webpageUrl.orEmpty() }
        if (target.isEmpty()) return
        downloads.enqueue(
            url = target,
            title = info.title ?: info.fulltitle ?: "Untitled",
            option = option
        )
        _events.tryEmit("Downloading ${option.label}: ${info.title ?: "video"}")
    }

    private fun prettify(message: String?): String {
        val msg = message ?: "Something went wrong"
        return when {
            msg.contains("is not a valid URL", ignoreCase = true) ->
                "That does not look like a valid link."
            msg.contains("Unable to parse video information", ignoreCase = true) ->
                "Could not read this link. Playlist pages are not supported yet - share a single video link."
            msg.contains("Unsupported URL", ignoreCase = true) ->
                "This website is not supported by yt-dlp."
            msg.contains("not a bot", ignoreCase = true) ||
                msg.contains("Sign in to confirm", ignoreCase = true) ->
                "This site wants to verify you are human (common on mobile networks). " +
                    "Try again, or sign in with the built-in browser in Settings > Cookies."
            msg.contains("cookie", ignoreCase = true) ->
                "This site needs login cookies. Open Settings > Cookies > Sign in with " +
                    "browser, log in to the site there, and Duck imports the cookies " +
                    "automatically (same as the Seal app)."
            msg.contains("age-restricted", ignoreCase = true) ||
                msg.contains("login required", ignoreCase = true) ||
                msg.contains("requested content is not available", ignoreCase = true) ->
                "This video needs an account. Use Settings > Cookies > Sign in with " +
                    "browser to log in and unlock it."
            else -> msg.take(300)
        }
    }
}
