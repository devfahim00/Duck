package com.devfahim00.duck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devfahim00.duck.downloads.DownloadManager
import com.devfahim00.duck.downloads.DownloadStatus
import com.devfahim00.duck.ui.common.DuckLogo
import com.devfahim00.duck.ui.downloads.DownloadsScreen
import com.devfahim00.duck.ui.home.HomeScreen
import com.devfahim00.duck.ui.home.HomeViewModel
import com.devfahim00.duck.ui.settings.SettingsSheet
import com.devfahim00.duck.ytdlp.YtDlpEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuckRoot(initialUrl: String?, onConsumeInitialUrl: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val homeViewModel: HomeViewModel = viewModel {
        HomeViewModel(YtDlpEngine, DownloadManager)
    }

    val list by DownloadManager.downloads.collectAsState()
    val activeCount = list.count {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DuckLogo(logoSize = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Duck")
                    }
                },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Text("Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Home") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(if (activeCount > 0) "Downloads ($activeCount)" else "Downloads")
                    }
                )
            }

            when (tab) {
                0 -> HomeScreen(viewModel = homeViewModel, initialUrl = initialUrl)
                else -> DownloadsScreen()
            }
        }
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) onConsumeInitialUrl()
    }
}
