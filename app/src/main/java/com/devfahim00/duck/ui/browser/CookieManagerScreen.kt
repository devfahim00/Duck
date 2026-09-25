package com.devfahim00.duck.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devfahim00.duck.R
import com.devfahim00.duck.ui.common.GlassCard
import com.devfahim00.duck.ui.common.IconBadge
import com.devfahim00.duck.ui.theme.DangerRed
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkLow
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.ui.theme.SpeedGradient
import com.devfahim00.duck.ui.theme.SpeedTeal
import com.devfahim00.duck.util.CookieStore
import com.devfahim00.duck.util.Settings

/**
 * "Manage cookies": every site currently holding cookies, listed separately.
 * Tap a site to expand it and copy, edit, or delete just that site's cookies
 * without touching any other site's login.
 */
@Composable
fun CookieManagerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sites by remember { mutableStateOf(CookieStore.sites(context)) }
    var expandedDomain by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        sites = CookieStore.sites(context)
        if (sites.none { it.domain == expandedDomain }) expandedDomain = null
    }

    BackHandler(enabled = true) { onClose() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        // Same teal "browser mode" identity as the built-in browser screen,
        // so it reads as part of that flow rather than a second home screen.
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowserModeHeader(
                icon = R.drawable.ic_cookie,
                title = "Manage cookies",
                subtitle = if (sites.isEmpty()) {
                    "No cookies stored yet"
                } else {
                    "${sites.size} site(s) - tap one to view, copy, edit or remove"
                },
                onClose = onClose
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                if (sites.isEmpty()) {
                    Text(
                        "Sign in with the built-in browser first, or import a cookies.txt file " +
                            "from Settings - sites will show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkLow,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                sites.forEach { site ->
                    SiteCookieCard(
                        site = site,
                        expanded = expandedDomain == site.domain,
                        onToggle = {
                            expandedDomain = if (expandedDomain == site.domain) null else site.domain
                        },
                        onCopy = {
                            val text = CookieStore.netscapeTextForSite(context, site.domain)
                            if (text != null) {
                                clipboard.setText(AnnotatedString(text))
                                toast = "Copied ${site.domain}'s cookies"
                            }
                        },
                        onSave = { entries ->
                            CookieStore.updateSite(context, site.domain, entries)
                            Settings.updateCookiesEnabled(true)
                            toast = "Saved ${site.domain}"
                            refresh()
                        },
                        onDelete = {
                            CookieStore.deleteSite(context, site.domain)
                            toast = "Removed ${site.domain}"
                            refresh()
                        }
                    )
                }

                toast?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpeedGradient.first(),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Distinct colored header shared by the browser-mode screens (built-in
 * browser + cookie manager): a teal icon badge and title bar that Home never
 * uses, so these full-screen modes are recognizable at a glance instead of
 * blending into Home's own url-bar-plus-button layout.
 */
@Composable
internal fun BrowserModeHeader(
    icon: Int,
    title: String,
    subtitle: String,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            IconBadge(
                painter = painterResource(icon),
                size = 38.dp,
                container = SpeedTeal.copy(alpha = 0.18f),
                tint = SpeedTeal
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = InkHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = InkHigh,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SiteCookieCard(
    site: CookieStore.SiteCookies,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onSave: (List<CookieStore.CookieEntry>) -> Unit,
    onDelete: () -> Unit
) {
    // Local editable copy so typing doesn't touch disk until "Save".
    var draft by remember(site.domain, expanded) { mutableStateOf(site.entries) }

    GlassCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.animateContentSize()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        site.domain.removePrefix("."),
                        style = MaterialTheme.typography.titleSmall,
                        color = InkHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${site.entries.size} cookie(s)",
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

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                draft.forEachIndexed { index, entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = entry.name,
                            onValueChange = { new ->
                                draft = draft.toMutableList().also { it[index] = entry.copy(name = new) }
                            },
                            modifier = Modifier.weight(0.4f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            label = { Text("Name", style = MaterialTheme.typography.labelSmall) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SpeedTeal,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        OutlinedTextField(
                            value = entry.value,
                            onValueChange = { new ->
                                draft = draft.toMutableList().also { it[index] = entry.copy(value = new) }
                            },
                            modifier = Modifier.weight(0.6f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            label = { Text("Value", style = MaterialTheme.typography.labelSmall) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SpeedTeal,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SiteActionButton(
                        text = "Copy",
                        icon = R.drawable.ic_paste,
                        modifier = Modifier.weight(1f),
                        onClick = onCopy
                    )
                    SiteActionButton(
                        text = "Save",
                        icon = R.drawable.ic_check_circle,
                        modifier = Modifier.weight(1f),
                        tint = SpeedTeal,
                        onClick = { onSave(draft) }
                    )
                    SiteActionButton(
                        text = "Delete",
                        icon = R.drawable.ic_delete,
                        modifier = Modifier.weight(1f),
                        tint = DangerRed,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteActionButton(
    text: String,
    icon: Int,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = InkHigh,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
