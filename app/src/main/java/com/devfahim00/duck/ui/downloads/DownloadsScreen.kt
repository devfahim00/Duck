package com.devfahim00.duck.ui.downloads

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devfahim00.duck.R
import com.devfahim00.duck.downloads.DownloadItem
import com.devfahim00.duck.downloads.DownloadManager
import com.devfahim00.duck.downloads.DownloadStatus
import com.devfahim00.duck.ui.common.ActionChip
import com.devfahim00.duck.ui.common.EmptyState
import com.devfahim00.duck.ui.common.GlassCard
import com.devfahim00.duck.ui.common.GradientProgressBar
import com.devfahim00.duck.ui.common.IndeterminateGradientBar
import com.devfahim00.duck.ui.common.statusColor
import com.devfahim00.duck.ui.common.statusGradient
import com.devfahim00.duck.ui.theme.DangerRed
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkLow
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.ui.theme.SpeedGradient
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Downloads",
                style = MaterialTheme.typography.titleLarge,
                color = InkHigh,
                modifier = Modifier.weight(1f)
            )
            if (finishedCount > 0) {
                ActionChip(
                    text = "Clear finished",
                    icon = painterResource(R.drawable.ic_close),
                    tint = InkMedium,
                    onClick = { DownloadManager.clearFinished() }
                )
            }
        }

        if (list.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                EmptyState(
                    icon = painterResource(R.drawable.ic_cloud_download),
                    title = "Nothing here yet",
                    subtitle = "Paste a link on the Home tab to start your first download."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        context = context,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// One download: status avatar, title, animated gradient progress, meta row
// and contextual actions.
// ---------------------------------------------------------------------------

@Composable
private fun DownloadCard(item: DownloadItem, context: Context, modifier: Modifier = Modifier) {
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusAvatar(item)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = InkHigh,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        FormatPill(item)
                        Text(
                            statusLabel(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor(item.status)
                        )
                    }
                }
                PercentBadge(item)
            }

            when (item.status) {
                DownloadStatus.QUEUED -> IndeterminateGradientBar(barHeight = 6.dp)
                DownloadStatus.DOWNLOADING -> {
                    if (item.merging) {
                        IndeterminateGradientBar(barHeight = 6.dp)
                        MetaRow(
                            left = "Merging video + audio…",
                            right = null,
                            highlight = false
                        )
                    } else {
                        // Real progress hasn't arrived yet (freshly started, or a
                        // slow/silent extractor) - show a live indeterminate sweep
                        // instead of a dead 0% bar, then cross-fade into the real
                        // determinate bar the moment actual percentages start
                        // flowing in. No more "stuck at 0%" look.
                        AnimatedContent(
                            targetState = item.progress > 0f,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                            label = "progressBarSwap"
                        ) { hasProgress ->
                            if (hasProgress) {
                                GradientProgressBar(
                                    progress = item.progress / 100f,
                                    barHeight = 6.dp,
                                    gradient = statusGradient(item.status)
                                )
                            } else {
                                IndeterminateGradientBar(barHeight = 6.dp)
                            }
                        }
                        MetaRow(
                            left = item.speed ?: "Connecting…",
                            right = if (item.etaSeconds >= 0) {
                                "ETA ${FileUtils.formatEta(item.etaSeconds)}"
                            } else {
                                null
                            },
                            highlight = true
                        )
                    }
                }

                DownloadStatus.COMPLETED -> {
                    val path = item.filePath
                    if (path != null) {
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkLow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ActionRow {
                        ActionChip(
                            text = "Open",
                            icon = painterResource(R.drawable.ic_play_arrow),
                            onClick = {
                                val p = item.filePath
                                if (p == null || !FileUtils.openFile(context, p)) {
                                    toast(context, "Could not open file")
                                }
                            }
                        )
                        ActionChip(
                            text = "Share",
                            icon = painterResource(R.drawable.ic_share),
                            onClick = {
                                val p = item.filePath
                                if (p == null || !FileUtils.shareFile(context, p)) {
                                    toast(context, "Could not share file")
                                }
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        ActionChip(
                            text = "Delete",
                            icon = painterResource(R.drawable.ic_delete),
                            tint = DangerRed,
                            onClick = { DownloadManager.delete(item.id) }
                        )
                    }
                }

                DownloadStatus.FAILED -> {
                    item.error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ActionRow {
                        ActionChip(
                            text = "Retry",
                            icon = painterResource(R.drawable.ic_refresh),
                            onClick = { DownloadManager.retry(item.id) }
                        )
                        Spacer(Modifier.weight(1f))
                        ActionChip(
                            text = "Delete",
                            icon = painterResource(R.drawable.ic_delete),
                            tint = DangerRed,
                            onClick = { DownloadManager.delete(item.id) }
                        )
                    }
                }

                DownloadStatus.CANCELLED -> {
                    ActionRow {
                        ActionChip(
                            text = "Download again",
                            icon = painterResource(R.drawable.ic_refresh),
                            onClick = { DownloadManager.retry(item.id) }
                        )
                        Spacer(Modifier.weight(1f))
                        ActionChip(
                            text = "Delete",
                            icon = painterResource(R.drawable.ic_delete),
                            tint = InkMedium,
                            onClick = { DownloadManager.delete(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusAvatar(item: DownloadItem) {
    val size = 40.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = item.status,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.7f)) togetherWith
                    fadeOut(tween(140))
            },
            label = "statusAvatar"
        ) { status ->
            when (status) {
                DownloadStatus.DOWNLOADING -> {
                    val progress = if (item.merging) 0.25f else item.progress / 100f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress.coerceIn(0.01f, 1f),
                        animationSpec = tween(350),
                        label = "avatarProgress"
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        strokeWidth = 3.dp,
                        color = statusGradient(status).first(),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        modifier = Modifier.size(size)
                    )
                }

                DownloadStatus.QUEUED -> AvatarIcon(R.drawable.ic_layers, InkMedium)

                DownloadStatus.COMPLETED -> AvatarIcon(R.drawable.ic_check_circle, statusColor(status))

                DownloadStatus.FAILED -> AvatarIcon(R.drawable.ic_error, DangerRed)

                DownloadStatus.CANCELLED -> AvatarIcon(R.drawable.ic_close, InkMedium)
            }
        }
    }
}

@Composable
private fun AvatarIcon(resId: Int, tint: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(resId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun PercentBadge(item: DownloadItem) {
    AnimatedVisibility(
        visible = item.status == DownloadStatus.DOWNLOADING && !item.merging,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150))
    ) {
        AnimatedContent(
            targetState = item.progress.toInt(),
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
            },
            label = "percent"
        ) { percent ->
            Text(
                "$percent%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor(item.status)
            )
        }
    }
}

@Composable
private fun FormatPill(item: DownloadItem) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            item.formatLabel,
            style = MaterialTheme.typography.labelSmall,
            color = InkMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun MetaRow(left: String, right: String?, highlight: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (highlight) {
            Icon(
                painter = painterResource(R.drawable.ic_bolt),
                contentDescription = null,
                tint = SpeedGradient.first(),
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.size(5.dp))
        }
        Text(
            left,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) SpeedGradient.first() else InkMedium,
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal
        )
        Spacer(Modifier.weight(1f))
        if (right != null) {
            Text(
                right,
                style = MaterialTheme.typography.bodySmall,
                color = InkMedium
            )
        }
    }
}

private fun statusLabel(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> if (item.merging) "Merging" else "Downloading"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
