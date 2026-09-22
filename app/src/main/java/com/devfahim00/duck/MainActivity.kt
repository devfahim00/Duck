package com.devfahim00.duck

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.devfahim00.duck.ui.theme.DuckTheme
import com.devfahim00.duck.util.FileUtils

class MainActivity : ComponentActivity() {

    private val sharedUrl = mutableStateOf<String?>(null)

    /** Re-checked in onCreate and every time we come back to the foreground
     *  (e.g. returning from the "All files access" settings screen). */
    private val storageGranted = mutableStateOf(false)

    // Android 11+ (API 30+): "All files access" only lives behind a dedicated
    // Settings screen, so we launch it and re-check the real permission state
    // when the user comes back rather than trusting the activity result code.
    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        storageGranted.value = FileUtils.hasStoragePermission(this)
    }

    // Android 6-10 (API 23-29): classic runtime permission dialog.
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        storageGranted.value = granted || FileUtils.hasStoragePermission(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Duck ships a permanently dark identity: always use light (dark-themed)
        // system bar icons on transparent bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleSharedIntent(intent)
        storageGranted.value = FileUtils.hasStoragePermission(this)
        setContent {
            DuckTheme {
                DuckRoot(
                    initialUrl = sharedUrl.value,
                    onConsumeInitialUrl = { sharedUrl.value = null },
                    storageGranted = storageGranted.value,
                    onRequestStoragePermission = ::requestStoragePermission
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches the user granting the permission from system Settings and
        // switching back to Duck without going through onActivityResult.
        storageGranted.value = FileUtils.hasStoragePermission(this)
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

    /** Kicks off the SDK-appropriate storage permission flow. */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = try {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", packageName, null)
                )
            } catch (e: Exception) {
                null
            }
            if (intent != null && intent.resolveActivity(packageManager) != null) {
                allFilesAccessLauncher.launch(intent)
            } else {
                // Some OEM builds don't resolve the per-app screen - fall back
                // to the general "All files access" settings list.
                allFilesAccessLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
