package com.devfahim00.duck.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devfahim00.duck.ui.common.DuckLogo
import com.devfahim00.duck.util.FileUtils
import com.devfahim00.duck.ytdlp.FormatOption
import com.yausername.youtubedl_android.mapper.VideoInfo

@Composable
fun HomeScreen(viewModel: HomeViewModel, initialUrl: String?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.fetchState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) viewModel.onUrlChange(initialUrl)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DuckLogo(logoSize = 44.dp)
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        "Duck",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "yt-dlp powered downloader",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("Video link") },
                placeholder = { Text("https://youtube.com/watch?v=...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val text = clipboard.getText()?.text ?: return@TextButton
                            val match = Regex("https?://\\S+").find(text)
                            viewModel.onUrlChange(match?.value ?: text.trim())
                        }
                    ) { Text("Paste") }
                }
            )

            val busy = state is FetchState.Loading
            Button(
                onClick = { viewModel.fetch(context) },
                enabled = viewModel.url.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Fetching formats..." else "Get formats")
            }

            when (val s = state) {
                is FetchState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is FetchState.Error -> {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is FetchState.Ready -> {
                    InfoHeader(s.info)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    s.options.forEach { option ->
                        FormatRow(option) { viewModel.startDownload(context, option) }
                    }
                }
                FetchState.Idle -> {
                    Text(
                        "Paste a video link from YouTube or any of the 1000+ sites supported by yt-dlp, then tap Get formats. " +
                            "Tip: use the Share button inside other apps to send links straight to Duck.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun InfoHeader(info: VideoInfo) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    info.title ?: "Untitled",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    info.uploader,
                    if (info.duration > 0) FileUtils.formatDuration(info.duration.toLong()) else null
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatRow(option: FormatOption, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val est = option.estBytes?.let { " - about ${FileUtils.formatSize(it)}" } ?: ""
                Text(
                    option.detail + est,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Download",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
