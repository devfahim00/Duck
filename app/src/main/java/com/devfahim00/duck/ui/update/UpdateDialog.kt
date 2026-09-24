package com.devfahim00.duck.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devfahim00.duck.R
import com.devfahim00.duck.ui.common.GlassCard
import com.devfahim00.duck.ui.common.GradientButton
import com.devfahim00.duck.ui.theme.DuckGradient
import com.devfahim00.duck.ui.theme.InkHigh
import com.devfahim00.duck.ui.theme.InkMedium
import com.devfahim00.duck.util.AppUpdater

/**
 * Shown automatically (once per launch, DuckRoot) when GitHub Releases has a
 * newer version than the installed build. "Download update" hands the APK URL
 * to the browser, which downloads it; tapping the finished file installs it.
 */
@Composable
fun UpdateDialog(
    release: AppUpdater.Release,
    currentVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60A0E16)),
        contentAlignment = Alignment.Center
    ) {
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
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(DuckGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_update),
                        contentDescription = null,
                        tint = Color(0xFF241800),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    "Update available",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = InkHigh,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Duck ${release.version} is out - you are on $currentVersion. " +
                        "Grab the new APK from GitHub Releases and install it to update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMedium,
                    textAlign = TextAlign.Center
                )

                GradientButton(
                    text = "Download ${release.version}",
                    icon = painterResource(R.drawable.ic_download),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(top = 4.dp),
                    onClick = onDownload
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
