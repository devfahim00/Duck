package com.devfahim00.duck.ui.downloads

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devfahim00.duck.downloads.DownloadItem
import com.devfahim00.duck.downloads.DownloadManager
import com.devfahim00.duck.downloads.DownloadStatus
import com.devfahim00.duck.ui.common.DuckLogo
import com.devfahim00.duck.util.FileUtils

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val list by DownloadManager.downloads.collectAsState()
    val finishedCount = list.count {
        it.status == DownloadStatus.COMPLETED ||
            it.status == DownloadStatus.FAILED ||
            it.status == DownloadStatus.CANCELLED
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Downloads",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (finishedCount > 0) {
                TextButton(onClick = { DownloadManager.clearFinished() }) {
                    Text("Clear finished")
                }
            }
        }

        if (list.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { item ->
                    DownloadRow(item, context)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DuckLogo(logoSize = 72.dp)
        Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste a link on the Home tab to start downloading.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${item.formatLabel} - ${statusLabel(item.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.status == DownloadStatus.DOWNLOADING) {
                    Text(
                        "${item.progress.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            when (item.status) {
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            item.speed
                                ?: if (item.status == DownloadStatus.QUEUED) "Waiting..."
                                else "Connecting...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (item.status == DownloadStatus.DOWNLOADING && item.etaSeconds >= 0) {
                            Text(
                                "ETA ${FileUtils.formatEta(item.etaSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { DownloadManager.cancel(item.id) }) {
                            Text("Cancel")
                        }
                    }
                }

                DownloadStatus.COMPLETED -> {
                    val path = item.filePath
                    if (path != null) {
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        TextButton(
                            onClick = {
                                val p = item.filePath
                                if (p == null || !FileUtils.openFile(context, p)) {
                                    toast(context, "Could not open file")
                                }
                            }
                        ) { Text("Open") }
                        TextButton(
                            onClick = {
                                val p = item.filePath
                                if (p == null || !FileUtils.shareFile(context, p)) {
                                    toast(context, "Could not share file")
                                }
                            }
                        ) { Text("Share") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { DownloadManager.delete(item.id) }) {
                            Text("Delete")
                        }
                    }
                }

                DownloadStatus.FAILED -> {
                    item.error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        TextButton(onClick = { DownloadManager.retry(item.id) }) {
                            Text("Retry")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { DownloadManager.delete(item.id) }) {
                            Text("Delete")
                        }
                    }
                }

                DownloadStatus.CANCELLED -> {
                    Row {
                        TextButton(onClick = { DownloadManager.retry(item.id) }) {
                            Text("Download again")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { DownloadManager.delete(item.id) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
