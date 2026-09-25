package com.devfahim00.duck

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devfahim00.duck.downloads.DownloadManager
import com.devfahim00.duck.downloads.DownloadStatus
import com.devfahim00.duck.ui.browser.CookieBrowserScreen
import com.devfahim00.duck.ui.browser.CookieManagerScreen
import com.devfahim00.duck.ui.common.DuckLogo
import com.devfahim00.duck.ui.common.GlassCard
import com.devfahim00.duck.ui.common.GradientButton
import com.devfahim00.duck.ui.downloads.DownloadsScreen
import com.devfahim00.duck.ui.home.HomeScreen
import com.devfahim00.duck.ui.home.HomeViewModel
import com.devfahim00.duck.ui.settings.SettingsScreen
import com.devfahim00.duck.ui.theme.DuckGradient
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.ui.theme.NightGradient
import com.devfahim00.duck.ui.theme.SpeedTeal
import com.devfahim00.duck.ui.update.UpdateDialog
import com.devfahim00.duck.util.AppUpdater
import com.devfahim00.duck.ytdlp.YtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val NavDark = Color(0xFF241800)

@Composable
fun DuckRoot(
    initialUrl: String?,
    onConsumeInitialUrl: () -> Unit,
    storageGranted: Boolean = true,
    onRequestStoragePermission: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    // Bumped every time the built-in cookie browser imports cookies, so the
    // settings sheet refreshes its cookies section when it becomes visible
    // again.
    var cookieImportTick by remember { mutableIntStateOf(0) }
    var showCookieBrowser by remember { mutableStateOf(false) }
    var showCookieManager by remember { mutableStateOf(false) }
    // "Maybe later" only dismisses the gate for this app session - Duck checks
    // again the next time the app is opened, per the requirement that storage
    // access is verified fresh on every launch.
    var permissionDismissedThisSession by rememberSaveable { mutableStateOf(false) }

    // ---- Automatic app update check (silent, once per launch) ----
    // Compares the installed version against the latest GitHub Release and
    // pops a dialog when a newer build is published. Manual checks live in
    // the Settings sheet.
    val context = LocalContext.current
    var updateRelease by remember { mutableStateOf<AppUpdater.Release?>(null) }
    var installedVersion by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        installedVersion = AppUpdater.currentVersion(context)
        val result = withContext(Dispatchers.IO) { AppUpdater.check(context) }
        updateRelease = (result as? AppUpdater.Result.Available)?.release
    }

    val homeViewModel: HomeViewModel = viewModel {
        HomeViewModel(YtDlpEngine, DownloadManager)
    }

    val list by DownloadManager.downloads.collectAsState()
    val activeCount = list.count {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(NightGradient))
            .clipToBounds()
    ) {
        // Ambient brand glows - warm amber from the top, teal from the bottom left.
        Box(
            Modifier
                .fillMaxWidth()
                .height(460.dp)
                .offset(y = (-170).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x1AFFC94D), Color(0x00FFC94D))
                    )
                )
        )
        Box(
            Modifier
                .size(420.dp)
                .offset(x = (-160).dp, y = 340.dp)
                .background(
                    Brush.radialGradient(
                        listOf(SpeedTeal.copy(alpha = 0.07f), Color(0x002DD4BF))
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            HeaderRow(onSettings = { showSettings = true })

            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (
                            slideInHorizontally(tween(260)) { it / 5 * direction } +
                                    fadeIn(tween(220))
                            ) togetherWith (
                            slideOutHorizontally(tween(200)) { -it / 5 * direction } +
                                    fadeOut(tween(160))
                            )
                },
                label = "tabContent"
            ) { current ->
                when (current) {
                    0 -> HomeScreen(viewModel = homeViewModel, initialUrl = initialUrl)
                    else -> DownloadsScreen()
                }
            }

            FloatingNavBar(
                selected = tab,
                activeCount = activeCount,
                onSelect = { tab = it }
            )
        }
    }

    if (showSettings) {
        SettingsScreen(
            onDismiss = { showSettings = false },
            onOpenCookieBrowser = {
                showSettings = false
                showCookieBrowser = true
            },
            onOpenCookieManager = {
                showSettings = false
                showCookieManager = true
            },
            cookieImportTick = cookieImportTick
        )
    }

    if (showCookieBrowser) {
        CookieBrowserScreen(
            onClose = { imported ->
                if (imported > 0) cookieImportTick++
                showCookieBrowser = false
            }
        )
    }

    if (showCookieManager) {
        CookieManagerScreen(
            onClose = { showCookieManager = false }
        )
    }

    updateRelease?.let { release ->
        UpdateDialog(
            release = release,
            currentVersion = installedVersion,
            onDownload = { AppUpdater.openLink(context, release.apkUrl) },
            onDismiss = { updateRelease = null }
        )
    }

    AnimatedVisibility(
        visible = !storageGranted && !permissionDismissedThisSession,
        enter = fadeIn(tween(260)),
        exit = fadeOut(tween(200))
    ) {
        StoragePermissionGate(
            onGrant = onRequestStoragePermission,
            onDismiss = { permissionDismissedThisSession = true }
        )
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) onConsumeInitialUrl()
    }
}

// ---------------------------------------------------------------------------
// Full-screen gate shown until storage access is granted. Explains why Duck
// needs it (so downloads land in the public Downloads/Duck folder instead of
// a private app-only folder) and hands off to the SDK-appropriate permission
// flow started from MainActivity.
// ---------------------------------------------------------------------------

@Composable
private fun StoragePermissionGate(onGrant: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60A0E16)),
        contentAlignment = Alignment.Center
    ) {
        val transition = rememberInfiniteTransition(label = "permissionPulse")
        val pulse by transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        GlassCard(
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(DuckGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_download),
                        contentDescription = null,
                        tint = Color(0xFF241800),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    "Storage access needed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = InkHigh,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Grant storage access so your downloads are saved to Downloads/Duck " +
                        "where you can find and share them from any app. Without it, files " +
                        "stay in a private folder only Duck can open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMedium,
                    textAlign = TextAlign.Center
                )

                GradientButton(
                    text = "Grant storage access",
                    icon = painterResource(R.drawable.ic_arrow_forward),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(top = 4.dp),
                    onClick = onGrant
                )

                Text(
                    "Maybe later",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkMedium,
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 2.dp)
                        .clickable(onClick = onDismiss)
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(onSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        DuckLogo(logoSize = 38.dp)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                "Duck",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = InkHigh
            )
            Text(
                "Video Downloader",
                style = MaterialTheme.typography.labelMedium,
                color = InkMedium
            )
        }
        Spacer(Modifier.weight(1f))
        SettingsButton(onClick = onSettings)
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Settings",
            tint = InkHigh,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FloatingNavBar(
    selected: Int,
    activeCount: Int,
    onSelect: (Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NavItem(
                    icon = painterResource(R.drawable.ic_home),
                    label = "Home",
                    selected = selected == 0,
                    badge = null,
                    onClick = { onSelect(0) }
                )
                NavItem(
                    icon = painterResource(R.drawable.ic_download),
                    label = "Downloads",
                    selected = selected == 1,
                    badge = activeCount.takeIf { it > 0 },
                    onClick = { onSelect(1) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: Painter,
    label: String,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) {
                        Brush.horizontalGradient(DuckGradient)
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .clickable(onClick = onClick)
                .animateContentSize(tween(220))
                .padding(horizontal = if (selected) 20.dp else 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (selected) NavDark else InkMedium,
                    modifier = Modifier.size(21.dp)
                )
                if (selected) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = NavDark
                    )
                }
            }
        }
        if (badge != null && !selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(SpeedTeal),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$badge",
                    color = Color(0xFF003733),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
