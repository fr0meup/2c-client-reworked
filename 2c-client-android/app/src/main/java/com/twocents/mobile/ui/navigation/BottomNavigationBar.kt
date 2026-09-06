package com.twocents.mobile.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold

@Composable
fun BottomNavigationBar(
    selectedTab: AppTab,
    backdropLayer: GraphicsLayer? = null,
    onSelectTab: (AppTab) -> Unit,
    unreadNotifications: Int = 0,
    unreadMessages: Int = 0,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val rawBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = when {
        rawBottomInset >= 40.dp -> (rawBottomInset - 6.dp).coerceAtLeast(2.dp)
        rawBottomInset > 0.dp -> rawBottomInset + 8.dp
        else -> 8.dp
    }
    var backdropTopPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(51.dp + bottomInset)
            .onGloballyPositioned { backdropTopPx = it.positionInRoot().y }
            .background(Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(0f, 0.5f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0.5f),
                    strokeWidth = 1f,
                )
            },
    ) {
        if (backdropLayer != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp, BlurredEdgeTreatment.Unbounded),
            ) {
                withTransform({ translate(0f, -backdropTopPx) }) {
                    drawLayer(backdropLayer)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background.copy(alpha = 0.94f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.012f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(51.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    badgeCount = when (tab) {
                        AppTab.Notifications -> unreadNotifications
                        AppTab.Messages -> unreadMessages
                        else -> 0
                    },
                    onClick = { AppHaptics.navigate(view); onSelectTab(tab) },
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: AppTab,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = tween(150),
        label = "tab-alpha",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)

    Box(
        modifier = Modifier
            .widthIn(min = 44.dp)
            .height(44.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TabIcon(
            tab = tab,
            selected = selected,
            modifier = Modifier
                .size(34.5.dp)
                .pressScale(pressScale)
                .graphicsLayer(alpha = iconAlpha),
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .widthIn(min = 19.dp)
                    .height(16.5.dp)
                    .clip(CircleShape)
                    .background(Background)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Gold)
                    .padding(horizontal = 2.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color(0xFF0F0E0A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 13.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
            }
        }
    }
}
