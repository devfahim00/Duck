package com.devfahim00.duck.ui.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.devfahim00.duck.R
import com.devfahim00.duck.ui.common.GradientButton
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkLow
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.ui.theme.SpeedGradient
import com.devfahim00.duck.util.CookieStore
import com.devfahim00.duck.util.Settings
import java.net.URI

/**
 * Built-in cookie browser (the Seal-style flow): the user opens a site,
 * logs in or passes its age/consent gate, and every cookie the site drops is
 * imported automatically when they tap Done - no cookies.txt export needed.
 *
 * Cookies accumulate in the WebView's persistent global CookieManager while
 * the user browses; on close we harvest them for every visited host into
 * [CookieStore] and auto-enable "Use cookies".
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieBrowserScreen(
    onClose: (importedCookies: Int) -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val visitedHosts = remember { mutableSetOf<String>() }

    fun finishImport() {
        val wv = webView
        val imported = CookieStore.exportFromBrowser(
            context = context,
            hosts = visitedHosts.toSet(),
            userAgent = wv?.settings?.userAgentString
        )
        if (imported != null && imported > 0) {
            Settings.updateCookiesEnabled(true)
        }
        wv?.stopLoading()
        onClose(imported ?: 0)
    }

    fun loadTypedUrl(raw: String) {
        val target = raw.trim()
        if (target.isEmpty()) return
        val withScheme = when {
            target.startsWith("http://", true) || target.startsWith("https://", true) -> target
            else -> "https://$target"
        }
        urlInput = withScheme
        webView?.loadUrl(withScheme)
    }

    BackHandler(enabled = true) {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else finishImport()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        // Distinct "browser mode" surface tone (surfaceContainer, not the
        // surfaceContainerLow every Home card sits on) plus the teal header
        // below - together they make this screen read as a different mode
        // instead of a second, confusingly similar copy of Home.
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            BrowserModeHeader(
                icon = R.drawable.ic_cookie,
                title = "Sign in with browser",
                subtitle = pageTitle.ifBlank { "Log in, then tap Done" },
                onClose = { finishImport() }
            )

            // ---- URL bar + Go ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Site address - e.g. xnxx.com",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkLow,
                            maxLines = 1
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { loadTypedUrl(urlInput) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = { loadTypedUrl(urlInput) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = "Go",
                        tint = InkHigh,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ---- Done button (page title now lives in the header above) ----
            GradientButton(
                text = "Done - import cookies",
                icon = painterResource(R.drawable.ic_check_circle),
                gradient = SpeedGradient,
                contentColor = Color(0xFF003733),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(46.dp),
                onClick = { finishImport() }
            )
            Spacer(Modifier.height(4.dp))

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "Log in or pass the site's age check here - cookies are imported " +
                    "automatically when you tap Done, so locked videos download in Duck.",
                style = MaterialTheme.typography.bodySmall,
                color = InkLow,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ---- The browser itself ----
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val self = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.loadsImagesAutomatically = true
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(self, true)
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    // Let http(s) load inside; block external
                                    // schemes (mailto:, intent:, market:...).
                                    return request?.url?.scheme?.startsWith("http") != true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url.isNullOrBlank()) return
                                    runCatching { URI(url).host }.getOrNull()
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { host -> visitedHosts += host }
                                    pageTitle = view?.title.orEmpty()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            webView = this
                        }
                    }
                )
            }
        }
    }

    // Safety net: if the screen leaves composition without Done (e.g. the
    // user backs out of the app), still harvest whatever was browsed so the
    // login is not lost.
    DisposableEffect(Unit) {
        onDispose {
            val wv = webView
            if (wv != null && visitedHosts.isNotEmpty()) {
                runCatching {
                    val imported = CookieStore.exportFromBrowser(
                        context,
                        visitedHosts.toSet(),
                        wv.settings?.userAgentString
                    )
                    if (imported != null && imported > 0) Settings.updateCookiesEnabled(true)
                }
            }
        }
    }
}
