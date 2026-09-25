package com.devfahim00.duck.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devfahim00.duck.R
import com.devfahim00.duck.ui.browser.BrowserModeHeader
import com.devfahim00.duck.ui.common.GlassCard
import com.devfahim00.duck.ui.common.GradientButton
import com.devfahim00.duck.ui.common.IconBadge
import com.devfahim00.duck.ui.theme.DangerGradient
import com.devfahim00.duck.ui.theme.DangerRed
import com.devfahim00.duck.ui.theme.DuckGradient
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkLow
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.ui.theme.SpeedGradient
import com.devfahim00.duck.util.AppUpdater
import com.devfahim00.duck.util.CookieStore
import com.devfahim00.duck.util.FileUtils
import com.devfahim00.duck.util.Settings
import com.devfahim00.duck.ytdlp.YtDlpEngine
import com.devfahim00.duck.ytdlp.YtDlpUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings used to be one long ModalBottomSheet with every card stacked on
 * top of the next - Storage, threads, turbo, cookies, engine, app updates,
 * Telegram all on one page. It's now a small full-screen flow: a home list
 * of segments (Downloads / Cookies & Sign-in / Engine & Updates / About),
 * each opening its own dedicated page so a single screen never shows more
 * than one topic at a time.
 */
private enum class SettingsPage { HOME, DOWNLOADS, COOKIES, ENGINE, ABOUT }

@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onOpenCookieBrowser: () -> Unit = {},
    onOpenCookieManager: () -> Unit = {},
    cookieImportTick: Int = 0
) {
    var page by remember { mutableStateOf(SettingsPage.HOME) }

    // Hardware/gesture back steps out of a segment page first, and only
    // closes Settings entirely once we're already back at the home list.
    BackHandler(enabled = true) {
        if (page != SettingsPage.HOME) page = SettingsPage.HOME else onDismiss()
    }

    when (page) {
        SettingsPage.HOME -> SettingsHomeScreen(
            onClose = onDismiss,
            onNavigate = { page = it }
        )
        SettingsPage.DOWNLOADS -> SettingsDownloadsScreen(
            onBack = { page = SettingsPage.HOME }
        )
        SettingsPage.COOKIES -> SettingsCookiesScreen(
            onBack = { page = SettingsPage.HOME },
            onOpenCookieBrowser = onOpenCookieBrowser,
            onOpenCookieManager = onOpenCookieManager,
            cookieImportTick = cookieImportTick
        )
        SettingsPage.ENGINE -> SettingsEngineScreen(
            onBack = { page = SettingsPage.HOME }
        )
        SettingsPage.ABOUT -> SettingsAboutScreen(
            onBack = { page = SettingsPage.HOME }
        )
    }
}

// ---------------------------------------------------------------------------
// Home: just the four segments, nothing else - so the entry point reads at
// a glance instead of needing a scroll to see what's there.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsHomeScreen(
    onClose: () -> Unit,
    onNavigate: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val cookieCount = remember { CookieStore.stats(context)?.sites }

    SettingsPageScaffold(
        icon = R.drawable.ic_settings,
        title = "Settings",
        subtitle = "Downloads, cookies, engine and more",
        onClose = onClose
    ) {
        SettingsNavRow(
            iconRes = R.drawable.ic_folder_open,
            title = "Downloads",
            subtitle = "Storage location, threads, simultaneous downloads, turbo",
            onClick = { onNavigate(SettingsPage.DOWNLOADS) }
        )
        SettingsNavRow(
            iconRes = R.drawable.ic_cookie,
            title = "Cookies & Sign-in",
            subtitle = if (cookieCount != null && cookieCount > 0) {
                "$cookieCount site(s) signed in"
            } else {
                "Unlock sites that need a login"
            },
            onClick = { onNavigate(SettingsPage.COOKIES) }
        )
        SettingsNavRow(
            iconRes = R.drawable.ic_update,
            title = "Engine & Updates",
            subtitle = "yt-dlp engine version, app updates",
            onClick = { onNavigate(SettingsPage.ENGINE) }
        )
        SettingsNavRow(
            iconRes = R.drawable.ic_link,
            title = "About",
            subtitle = "Telegram, version, links",
            onClick = { onNavigate(SettingsPage.ABOUT) }
        )
    }
}

@Composable
private fun SettingsNavRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            IconBadge(
                painter = painterResource(iconRes),
                size = 40.dp,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                tint = InkHigh
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = InkHigh)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(12.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = InkLow,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Downloads: storage location, threads, simultaneous downloads, turbo.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsDownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    SettingsPageScaffold(
        icon = R.drawable.ic_folder_open,
        title = "Downloads",
        subtitle = "Storage, threads and speed",
        onClose = onBack
    ) {
        // ----- Storage -----
        SectionCard(
            iconRes = R.drawable.ic_folder_open,
            title = "Storage",
            subtitle = FileUtils.storageModeText(context)
        ) {
            if (!FileUtils.hasAllFilesAccess()) {
                Text(
                    "Grant All Files Access to save videos into the public Downloads folder, " +
                        "where every app and gallery can see them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMedium
                )
                GradientButton(
                    text = "Grant access",
                    icon = painterResource(R.drawable.ic_folder_open),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + context.packageName)
                                )
                            )
                        } catch (e: Exception) {
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                )
                            }
                        }
                    }
                )
            }
            Text(
                FileUtils.effectiveLocationText(context),
                style = MaterialTheme.typography.bodySmall,
                color = InkLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ----- Download threads -----
        SectionCard(
            iconRes = R.drawable.ic_speed,
            title = "Download threads",
            subtitle = "Connections per download - more threads means faster downloads on fast networks. Up to ${Settings.MAX_THREADS}."
        ) {
            PillSelector(
                options = listOf(4, 8, 16, 32),
                selected = Settings.threads,
                onSelect = { Settings.updateThreads(it) }
            )
        }

        // ----- Simultaneous downloads -----
        SectionCard(
            iconRes = R.drawable.ic_layers,
            title = "Simultaneous downloads",
            subtitle = "How many downloads run at the same time."
        ) {
            PillSelector(
                options = listOf(1, 2, 3),
                selected = Settings.parallelDownloads,
                onSelect = { Settings.updateParallel(it) }
            )
        }

        // ----- Turbo downloader -----
        GlassCard(shape = RoundedCornerShape(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                IconBadge(
                    painter = painterResource(R.drawable.ic_bolt),
                    size = 40.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tint = SpeedGradient.first()
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Turbo downloader",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkHigh
                    )
                    Text(
                        "Segmented multi-connection downloads for maximum speed (aria2c)",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMedium
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = Settings.turboAria2,
                    onCheckedChange = { Settings.updateTurbo(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = DuckGradient.first(),
                        checkedThumbColor = InkHigh,
                        checkedBorderColor = DuckGradient.first(),
                        checkedIconColor = Color(0xFF241800)
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cookies & Sign-in: its own page, unchanged behavior from before.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsCookiesScreen(
    onBack: () -> Unit,
    onOpenCookieBrowser: () -> Unit,
    onOpenCookieManager: () -> Unit,
    cookieImportTick: Int
) {
    val context = LocalContext.current
    var cookiesPresent by remember { mutableStateOf(CookieStore.exists(context)) }
    var cookieStats by remember { mutableStateOf(CookieStore.stats(context)) }
    var cookiesMessage by remember { mutableStateOf<String?>(null) }
    val cookiePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val error = CookieStore.import(context, uri)
        if (error == null) {
            cookiesPresent = true
            cookieStats = CookieStore.stats(context)
            Settings.updateCookiesEnabled(true)
            cookiesMessage = null
        } else {
            cookiesMessage = error
        }
    }

    // Fresh cookie state whenever the built-in browser imported something.
    LaunchedEffect(cookieImportTick) {
        cookiesPresent = CookieStore.exists(context)
        cookieStats = CookieStore.stats(context)
        if (cookiesPresent) cookiesMessage = null
    }

    SettingsPageScaffold(
        icon = R.drawable.ic_cookie,
        title = "Cookies & Sign-in",
        subtitle = "Unlock sites that need a login",
        onClose = onBack
    ) {
        SectionCard(
            iconRes = R.drawable.ic_cookie,
            title = "Cookies",
            subtitle = when {
                cookieStats != null ->
                    "${cookieStats!!.cookies} cookies for ${cookieStats!!.sites} site(s) - " +
                        "unlocks sites that need a login"
                cookiesPresent ->
                    "cookies.txt imported - unlocks sites that need a login"
                else ->
                    "Some sites (Instagram, Facebook, age-restricted YouTube, xnxx…) only " +
                        "work with cookies from a logged-in browser"
            }
        ) {
            GradientButton(
                text = "Sign in with browser",
                icon = painterResource(R.drawable.ic_cookie),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                onClick = onOpenCookieBrowser
            )
            Text(
                "Opens a built-in browser: log in to the site (or pass its age check) " +
                    "and tap Done - cookies are imported automatically, Seal-style. " +
                    "No cookies.txt export needed.",
                style = MaterialTheme.typography.bodySmall,
                color = InkLow
            )
            if (cookiesPresent || cookieStats != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Use cookies",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkHigh
                        )
                        Text(
                            "Attached to every fetch and download",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMedium
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Switch(
                        checked = Settings.cookiesEnabled,
                        onCheckedChange = { Settings.updateCookiesEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = DuckGradient.first(),
                            checkedThumbColor = InkHigh,
                            checkedBorderColor = DuckGradient.first(),
                            checkedIconColor = Color(0xFF241800)
                        )
                    )
                }
                GradientButton(
                    text = "Import cookies.txt instead",
                    icon = painterResource(R.drawable.ic_paste),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    onClick = {
                        cookiePicker.launch(
                            arrayOf("text/plain", "application/octet-stream", "*/*")
                        )
                    }
                )
                GradientButton(
                    text = "Manage cookies",
                    icon = painterResource(R.drawable.ic_cookie),
                    gradient = SpeedGradient,
                    contentColor = Color(0xFF003733),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    onClick = onOpenCookieManager
                )
                GradientButton(
                    text = "Remove all cookies",
                    icon = painterResource(R.drawable.ic_delete),
                    gradient = DangerGradient,
                    contentColor = Color(0xFF2B0A0A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    onClick = {
                        CookieStore.clear(context)
                        Settings.updateCookiesEnabled(false)
                        cookiesPresent = false
                        cookieStats = null
                        cookiesMessage = null
                    }
                )
            }
            cookiesMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Engine & Updates: yt-dlp engine version + app updates.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsEngineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var updating by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<String?>(null) }
    var engineVersion by remember { mutableStateOf<String?>(null) }

    var checking by remember { mutableStateOf(false) }
    var appUpdateMessage by remember { mutableStateOf<String?>(null) }
    var appUpdateRelease by remember { mutableStateOf<AppUpdater.Release?>(null) }

    LaunchedEffect(Unit) {
        engineVersion = withContext(Dispatchers.IO) {
            runCatching { YtDlpEngine.awaitInitialized(context) }
            YtDlpUpdater.currentVersion(context)
        }
    }

    LaunchedEffect(updating) {
        if (!updating) return@LaunchedEffect
        updateResult = withContext(Dispatchers.IO) {
            when (val result = YtDlpUpdater.update(context)) {
                is YtDlpUpdater.Result.Success -> result.message
                is YtDlpUpdater.Result.Failure -> result.message
            }
        }
        engineVersion = withContext(Dispatchers.IO) { YtDlpUpdater.currentVersion(context) }
        updating = false
    }

    // Manual "Check for updates": asks GitHub Releases for the latest
    // version and reports back.
    LaunchedEffect(checking) {
        if (!checking) return@LaunchedEffect
        when (val result = withContext(Dispatchers.IO) { AppUpdater.check(context) }) {
            is AppUpdater.Result.UpToDate -> {
                appUpdateRelease = null
                appUpdateMessage = "You are up to date - Duck ${result.currentVersion} is the latest version."
            }
            is AppUpdater.Result.Available -> {
                appUpdateMessage = null
                appUpdateRelease = result.release
            }
            is AppUpdater.Result.Error -> {
                appUpdateMessage = "Check failed: ${result.message}"
            }
        }
        checking = false
    }

    SettingsPageScaffold(
        icon = R.drawable.ic_update,
        title = "Engine & Updates",
        subtitle = "yt-dlp engine and app updates",
        onClose = onBack
    ) {
        // ----- Engine -----
        SectionCard(
            iconRes = R.drawable.ic_update,
            title = "yt-dlp engine",
            subtitle = if (engineVersion != null) {
                "Version $engineVersion - keeps itself updated automatically on every launch"
            } else {
                "Bundled with the app - check for the latest release"
            }
        ) {
            GradientButton(
                text = if (updating) "Updating…" else "Update yt-dlp",
                icon = painterResource(R.drawable.ic_update),
                enabled = !updating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                onClick = { updating = true }
            )
            updateResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("Update failed")) DangerRed else InkMedium
                )
            }
        }

        // ----- App updates -----
        SectionCard(
            iconRes = R.drawable.ic_cloud_download,
            title = "App updates",
            subtitle = "Duck checks GitHub Releases automatically on launch - " +
                "or check yourself right now."
        ) {
            GradientButton(
                text = if (checking) "Checking…" else "Check for updates",
                icon = painterResource(R.drawable.ic_refresh),
                enabled = !checking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                onClick = { checking = true }
            )
            appUpdateRelease?.let { release ->
                Text(
                    "Duck ${release.version} is available!",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpeedGradient.first()
                )
                GradientButton(
                    text = "Download ${release.version}",
                    icon = painterResource(R.drawable.ic_download),
                    gradient = SpeedGradient,
                    contentColor = Color(0xFF003733),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    onClick = { AppUpdater.openLink(context, release.apkUrl) }
                )
            }
            appUpdateMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("Check failed")) DangerRed else InkMedium
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// About: Telegram + installed version.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var installedVersion by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        installedVersion = withContext(Dispatchers.IO) { AppUpdater.currentVersion(context) }
    }

    SettingsPageScaffold(
        icon = R.drawable.ic_link,
        title = "About",
        subtitle = if (installedVersion.isNotBlank()) "Duck $installedVersion" else "Duck",
        onClose = onBack
    ) {
        SectionCard(
            iconRes = R.drawable.ic_link,
            title = "Telegram",
            subtitle = "News, updates and support from the owner - ${AppUpdater.TELEGRAM_HANDLE}"
        ) {
            GradientButton(
                text = "Open Telegram",
                icon = painterResource(R.drawable.ic_link),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                onClick = { AppUpdater.openLink(context, AppUpdater.TELEGRAM_URL) }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared page chrome + cards
// ---------------------------------------------------------------------------

/**
 * Full-screen page shell every Settings segment shares: the same teal
 * "browser mode" header used by the built-in browser and cookie manager
 * (so it's immediately recognizable as its own page rather than a re-skinned
 * Home), plus a scrollable, padded content column.
 */
@Composable
private fun SettingsPageScaffold(
    icon: Int,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowserModeHeader(
                icon = icon,
                title = title,
                subtitle = subtitle,
                onClose = onClose
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                content()
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    GlassCard(shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    painter = painterResource(iconRes),
                    size = 40.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tint = InkHigh
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = InkHigh
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMedium
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun PillSelector(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { value ->
            val isSelected = value == selected
            val shape = RoundedCornerShape(50)
            val unselected = MaterialTheme.colorScheme.surfaceContainerHighest
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            Brush.horizontalGradient(DuckGradient)
                        } else {
                            Brush.horizontalGradient(listOf(unselected, unselected))
                        }
                    )
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$value",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF241800) else InkHigh,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}
