package com.devfahim00.duck.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // ----- Storage -----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    FileUtils.storageModeText(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!FileUtils.hasAllFilesAccess()) {
                    Text(
                        "Grant All Files Access to save videos into the public Downloads folder, " +
                            "where every app and gallery can see them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
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
                    ) { Text("Grant All Files Access") }
                }
                Text(
                    "Current folder: ${FileUtils.effectiveLocationText(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ----- Download threads -----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Download threads",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Connections per download. More threads = faster downloads on fast networks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 8, 16).forEach { t ->
                        OutlinedButton(
                            onClick = { Settings.setThreads(t) },
                            enabled = Settings.threads != t
                        ) { Text("$t") }
                    }
                }
            }

            // ----- Simultaneous downloads -----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Simultaneous downloads",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "How many downloads run at the same time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { p ->
                        OutlinedButton(
                            onClick = { Settings.setParallel(p) },
                            enabled = Settings.parallelDownloads != p
                        ) { Text("$p") }
                    }
                }
            }

            // ----- Turbo downloader -----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Turbo downloader (aria2c)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Segmented multi-connection downloads for maximum speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = Settings.turboAria2,
                    onCheckedChange = { Settings.setTurbo(it) }
                )
            }

            // ----- Engine -----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (engineVersion != null) "yt-dlp engine: $engineVersion"
                    else "yt-dlp engine",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { updating = true },
                        enabled = !updating
                    ) {
                        if (updating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Updating...")
                        } else {
                            Text("Update yt-dlp")
                        }
                    }
                }
                updateResult?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.size(24.dp))
        }
    }
}
