package com.twocents.mobile.ui.common

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.twocents.mobile.kotlin.R

@Composable
fun TwoCentsLogo(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
) {
    Image(
        painter = painterResource(R.drawable.twocents_header_logo),
        contentDescription = "2C",
        contentScale = ContentScale.Fit,
        alignment = alignment,
        modifier = modifier,
    )
}
