package com.devfahim00.duck.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.devfahim00.duck.ui.common.GlassCard
import com.devfahim00.duck.ui.common.GradientButton
import com.devfahim00.duck.ui.common.IconBadge
import com.devfahim00.duck.ui.theme.DangerRed
import com.devfahim00.duck.ui.theme.DuckGradient
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkLow
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.ui.theme.SpeedGradient
import com.devfahim00.duck.util.FileUtils
import com.devfahim00.duck.util.Settings
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var updating by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<String?>(null) }
    var engineVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        engineVersion = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
    }

    LaunchedEffect(updating) {
        if (!updating) return@LaunchedEffect
        updateResult = withContext(Dispatchers.IO) {
            runCatching {
                when (YoutubeDL.getInstance().updateYoutubeDL(context)) {
                    YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated to the latest release"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date"
                    null -> "Unknown update result"
                }
            }.getOrElse { "Update failed: ${it.message?.take(120)}" }
        }
        engineVersion = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
        updating = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = InkHigh,
                modifier = Modifier.padding(bottom = 2.dp)
            )

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
                subtitle = "Connections per download - more threads means faster downloads on fast networks."
            ) {
                PillSelector(
                    options = listOf(4, 8, 16),
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

            // ----- Engine -----
            SectionCard(
                iconRes = R.drawable.ic_update,
                title = "yt-dlp engine",
                subtitle = if (engineVersion != null) {
                    "Version $engineVersion"
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

            Spacer(Modifier.height(26.dp))
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
