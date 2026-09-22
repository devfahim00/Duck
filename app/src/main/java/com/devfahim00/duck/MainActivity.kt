package com.devfahim00.duck

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.devfahim00.duck.ui.theme.DuckTheme

class MainActivity : ComponentActivity() {

    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Duck ships a permanently dark identity: always use light (dark-themed)
        // system bar icons on transparent bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleSharedIntent(intent)
        setContent {
            DuckTheme {
                DuckRoot(
                    initialUrl = sharedUrl.value,
                    onConsumeInitialUrl = { sharedUrl.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    /** Accepts links shared from other apps (YouTube etc.) and extracts the URL. */
    private fun handleSharedIntent(intent: Intent?) {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: return
        val match = Regex("https?://\\S+").find(text)
        if (match != null) sharedUrl.value = match.value
    }
}
