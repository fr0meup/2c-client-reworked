package com.twocents.mobile.ui.feed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.twocents.mobile.ui.common.TwoCentsLogo
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.HapticIntent

@Composable
internal fun HeaderLogoButton(
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .width(142.dp)
            .height(38.dp),
    ) {
        TwoCentsLogo(Modifier.width(55.dp).height(40.dp))
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .clickable { AppHaptics.navigate(view); onClick() },
        )
    }
}

@Composable
internal fun HeaderActionButton(
    @DrawableRes imageRes: Int,
    @DrawableRes pressedImageRes: Int = imageRes,
    iconSize: androidx.compose.ui.unit.Dp = 27.dp,
    fallback: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    hapticIntent: HapticIntent = HapticIntent.Navigate,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { AppHaptics.perform(view, hapticIntent); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .pressScale(rememberPressScale(interactionSource)),
            contentAlignment = Alignment.Center,
        ) {
            HeaderActionImage(
                imageRes = if (pressed || selected) pressedImageRes else imageRes,
                fallback = fallback,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
internal fun HeaderActionImage(
    @DrawableRes imageRes: Int,
    fallback: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
) {
    Image(
        painter = painterResource(imageRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
