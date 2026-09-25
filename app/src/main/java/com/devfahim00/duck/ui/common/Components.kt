package com.devfahim00.duck.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devfahim00.duck.downloads.DownloadStatus
import com.devfahim00.duck.ui.theme.DangerGradient
import com.devfahim00.duck.ui.theme.DangerRed
import com.devfahim00.duck.ui.theme.DuckAmber
import com.devfahim00.duck.ui.theme.DuckGradient
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.ui.theme.SuccessGradient
import com.devfahim00.duck.ui.theme.SuccessGreen

// ---------------------------------------------------------------------------
// Translucent "glass" card: the base container of the whole UI.
// ---------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = container,
        border = border,
        content = content
    )
}

// ---------------------------------------------------------------------------
// Gradient button with press-scale feedback.
// ---------------------------------------------------------------------------

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    gradient: List<Color> = DuckGradient,
    contentColor: Color = Color(0xFF241800),
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    val disabledContainer = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(gradient)
                } else {
                    Brush.horizontalGradient(listOf(disabledContainer, disabledContainer))
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // NOTE: buttons are placed with fixed heights as low as 42.dp
            // (see CookieBrowserScreen's "Done" button). 15.dp vertical padding
            // on both sides plus the 20.dp icon needs ~50.dp of content height,
            // which got clipped by the RoundedCornerShape above - the icon and
            // text visibly squished into the corners. 10.dp fits every caller.
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else InkMedium,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) contentColor else InkMedium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Animated gradient progress bar with a sweeping shimmer while active,
// plus an indeterminate variant for queued / merging states.
// ---------------------------------------------------------------------------

@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    barHeight: Dp = 8.dp,
    gradient: List<Color> = DuckGradient
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "progress"
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(shape)
                .background(Brush.horizontalGradient(gradient))
        ) {
            if (active && animated > 0.02f) {
                ShimmerOverlay(Modifier.matchParentSize())
            }
        }
    }
}

@Composable
fun IndeterminateGradientBar(
    modifier: Modifier = Modifier,
    barHeight: Dp = 8.dp,
    gradient: List<Color> = DuckGradient
) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "x"
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        // The moving block is fully off-screen at both x=0 (parked just left of
        // the bar) and x=1 (parked just right of it), so the loop restart is
        // invisible - no more "teleporting" pop back to the start.
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.36f)
                .graphicsLayer { translationX = (x * 1.72f - 0.36f) * size.width }
                .clip(shape)
                .background(Brush.horizontalGradient(gradient))
        )
    }
}

// ---------------------------------------------------------------------------
// Skeleton loading: shimmering placeholder blocks that mirror the shape of
// the real content, used while data is being fetched (e.g. analyzing a link).
// A single shared shimmer sweep is driven from the parent so every block in
// a skeleton group animates in sync, like one sheet of light passing over
// the whole card.
// ---------------------------------------------------------------------------

@Composable
fun rememberShimmerProgress(durationMillis: Int = 1400): Float {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "skeletonX"
    )
    return x
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shimmerProgress: Float,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = InkHigh.copy(alpha = 0.10f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(base)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { translationX = (shimmerProgress * 2.6f - 0.8f) * size.width }
                .background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, highlight, Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun ShimmerOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "x"
    )
    Box(
        modifier = modifier.graphicsLayer { translationX = (x * 1.5f - 0.5f) * size.width },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
        )
    }
}

// ---------------------------------------------------------------------------
// Small pill chip used for row actions (Cancel / Open / Share / Delete / Retry).
// ---------------------------------------------------------------------------

@Composable
fun ActionChip(
    text: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    tint: Color = InkHigh,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = 12.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Status dot with optional pulse, e.g. "Downloading" with a breathing dot.
// ---------------------------------------------------------------------------

@Composable
fun StatusDot(color: Color, pulse: Boolean = false, size: Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulse) 0.35f else 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900)),
        label = "alpha"
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color)
    )
}

// ---------------------------------------------------------------------------
// Circular icon badge used in settings sections and empty states.
// ---------------------------------------------------------------------------

@Composable
fun IconBadge(
    painter: Painter,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tint: Color = InkHigh
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ---------------------------------------------------------------------------
// Centered empty state block.
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconBadge(
            painter = icon,
            size = 68.dp,
            container = MaterialTheme.colorScheme.surfaceContainer,
            tint = InkMedium
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = InkHigh
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = InkMedium,
            modifier = Modifier.width(260.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Status color / gradient mapping shared by the downloads UI.
// ---------------------------------------------------------------------------

fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.QUEUED -> InkMedium
    DownloadStatus.DOWNLOADING -> DuckAmber
    DownloadStatus.COMPLETED -> SuccessGreen
    DownloadStatus.FAILED -> DangerRed
    DownloadStatus.CANCELLED -> InkMedium
}

fun statusGradient(status: DownloadStatus): List<Color> = when (status) {
    DownloadStatus.COMPLETED -> SuccessGradient
    DownloadStatus.FAILED -> DangerGradient
    else -> DuckGradient
}

// ---------------------------------------------------------------------------
// Thin spinner for the "connecting" phase of a download.
// ---------------------------------------------------------------------------

@Composable
fun ConnectingSpinner(modifier: Modifier = Modifier, color: Color = DuckAmber) {
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.size(16.dp)
    )
}
