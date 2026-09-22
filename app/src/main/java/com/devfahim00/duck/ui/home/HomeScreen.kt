package com.devfahim00.duck.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devfahim00.duck.R
import com.devfahim00.duck.ui.common.GlassCard
import com.devfahim00.duck.ui.common.GradientButton
import com.devfahim00.duck.ui.common.SkeletonBlock
import com.devfahim00.duck.ui.common.rememberShimmerProgress
import com.devfahim00.duck.ui.theme.DangerRed
import com.devfahim00.duck.ui.theme.DuckGradient
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkLow
import com.devfahim00.duck.ui.theme.InkMedium
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val busy = state is FetchState.Loading

            // ----- hero (only while idle) -----
            AnimatedVisibility(
                visible = state is FetchState.Idle,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 },
                exit = fadeOut(tween(180))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Download anything.",
                        style = MaterialTheme.typography.displaySmall,
                        color = InkHigh
                    )
                    Text(
                        "Paste a video link - YouTube and 1000+ sites supported,\nwith every quality to choose from.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMedium
                    )
                }
            }

            // ----- url field -----
            UrlField(
                value = viewModel.url,
                onValueChange = viewModel::onUrlChange,
                onPaste = {
                    val text = clipboard.getText()?.text ?: return@UrlField
                    val match = Regex("https?://\\S+").find(text)
                    viewModel.onUrlChange(match?.value ?: text.trim())
                },
                onClear = { viewModel.onUrlChange("") }
            )

            // ----- analyze button -----
            GradientButton(
                text = if (busy) "Fetching formats…" else "Analyze link",
                icon = painterResource(R.drawable.ic_arrow_forward),
                enabled = viewModel.url.isNotBlank() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                onClick = { viewModel.fetch(context) }
            )

            when (val s = state) {
                is FetchState.Loading -> LoadingSkeleton()

                is FetchState.Error -> ErrorCard(s.message)

                is FetchState.Ready -> {
                    RevealIn(delayMillis = 0) { VideoInfoCard(s.info) }
                    RevealIn(delayMillis = 60) {
                        Text(
                            "Choose quality",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkHigh,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    s.options.forEachIndexed { index, option ->
                        RevealIn(delayMillis = 90 + index * 45) {
                            FormatRow(
                                option = option,
                                highlight = index == 0 && !option.audioOnly,
                                onClick = { viewModel.startDownload(context, option) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                FetchState.Idle -> ShareHint()
            }
            Spacer(Modifier.height(12.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ---------------------------------------------------------------------------
// URL input styled as a glass search bar.
// ---------------------------------------------------------------------------

@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) DuckGradient.first() else MaterialTheme.colorScheme.outlineVariant

    GlassCard(
        shape = RoundedCornerShape(18.dp),
        container = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_link),
                contentDescription = null,
                tint = if (focused) DuckGradient.first() else InkMedium,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkHigh),
                cursorBrush = SolidColor(DuckGradient.first()),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                "https://youtube.com/watch?v=…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = InkLow
                            )
                        }
                        inner()
                    }
                }
            )
            if (value.isNotEmpty()) {
                ClearIcon(onClear)
            } else {
                PasteChip(onPaste)
            }
        }
    }
}

@Composable
private fun PasteChip(onPaste: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        onClick = onPaste
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_paste),
                contentDescription = null,
                tint = InkMedium,
                modifier = Modifier.size(13.dp)
            )
            Text(
                "Paste",
                style = MaterialTheme.typography.labelMedium,
                color = InkHigh
            )
        }
    }
}

@Composable
private fun ClearIcon(onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClear),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Clear",
            tint = InkMedium,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// States below the action button.
// ---------------------------------------------------------------------------

@Composable
private fun ShareHint() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_share),
            contentDescription = null,
            tint = InkLow,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Tip: use the Share button inside other apps to send links straight to Duck.",
            style = MaterialTheme.typography.bodySmall,
            color = InkLow
        )
    }
}

// ---------------------------------------------------------------------------
// Small fade + slide-up reveal, staggered per item, so the "Ready" state
// (video card + quality rows) settles in gracefully instead of popping in
// all at once.
// ---------------------------------------------------------------------------

@Composable
private fun RevealIn(delayMillis: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 5 }
    ) {
        content()
    }
}

@Composable
private fun LoadingSkeleton() {
    // One shared shimmer sweep drives every block below, so the whole card
    // reads as a single sheet of light passing over it - a proper skeleton
    // screen shaped like the video card it's about to become, instead of a
    // generic progress bar standing in for "loading".
    val shimmer = rememberShimmerProgress()

    GlassCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shimmerProgress = shimmer,
                shape = RoundedCornerShape(14.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp),
                    shimmerProgress = shimmer
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp),
                    shimmerProgress = shimmer
                )
            }
            Spacer(Modifier.height(2.dp))
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(14.dp),
                shimmerProgress = shimmer
            )
            repeat(2) {
                SkeletonFormatRow(shimmer)
            }
        }
    }
}

@Composable
private fun SkeletonFormatRow(shimmer: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        SkeletonBlock(
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
            shimmerProgress = shimmer,
            shape = RoundedCornerShape(11.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(13.dp),
                shimmerProgress = shimmer
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(11.dp),
                shimmerProgress = shimmer
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = InkHigh
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Ready state: video card + quality rows.
// ---------------------------------------------------------------------------

@Composable
private fun VideoInfoCard(info: VideoInfo) {
    GlassCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                )
                if (info.duration > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xCC05080D),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            FileUtils.formatDuration(info.duration.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkHigh,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    info.title ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkHigh,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    info.uploader,
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatRow(
    option: FormatOption,
    highlight: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    GlassCard(
        shape = shape,
        border = if (highlight) {
            BorderStroke(1.5.dp, Brush.horizontalGradient(DuckGradient))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            QualityBadge(shortLabel(option), highlight = highlight)
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = InkHigh
                    )
                    if (highlight) {
                        Text(
                            "RECOMMENDED",
                            style = MaterialTheme.typography.labelSmall,
                            color = DuckGradient.first(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                val est = option.estBytes?.let { " ≈ ${FileUtils.formatSize(it)}" } ?: ""
                Text(
                    option.detail + est,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMedium
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = InkLow,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun QualityBadge(label: String, highlight: Boolean) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (highlight) {
                    Brush.horizontalGradient(DuckGradient)
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFF241800) else InkHigh
        )
    }
}

private fun shortLabel(option: FormatOption): String = when {
    option.audioOnly -> "AUDIO"
    option.label == "Best quality" -> "BEST"
    else -> option.label.substringBefore(" ")
}
