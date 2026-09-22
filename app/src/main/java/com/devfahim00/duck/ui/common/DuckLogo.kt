package com.devfahim00.duck.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.devfahim00.duck.R
import com.devfahim00.duck.ui.theme.DuckGradient

/**
 * Brand mark: the golden duck on a warm amber gradient disc,
 * framed by a hairline ring. Scales cleanly from 24dp chips to hero sizes.
 */
@Composable
fun DuckLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(logoSize)
            .clip(CircleShape)
            .background(Brush.linearGradient(DuckGradient)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_duck),
            contentDescription = "Duck logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .matchParentSize()
                .padding(logoSize * 0.10f)
        )
    }
}
