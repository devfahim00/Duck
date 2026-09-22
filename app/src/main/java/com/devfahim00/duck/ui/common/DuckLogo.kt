package com.devfahim00.duck.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.devfahim00.duck.R

@Composable
fun DuckLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_duck_fg),
        contentDescription = "Duck logo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(logoSize)
            .clip(CircleShape)
    )
}
