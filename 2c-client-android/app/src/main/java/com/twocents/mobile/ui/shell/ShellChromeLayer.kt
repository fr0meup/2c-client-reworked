package com.twocents.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.compose.ComposePostFab
import com.twocents.mobile.ui.feed.TopicDropdownOverlay
import com.twocents.mobile.ui.navigation.AppTab
import com.twocents.mobile.ui.navigation.BottomNavigationBar

/** Renders shell chrome only; navigation state and action ordering stay owned by [MainShell]. */
@Composable
internal fun BoxScope.ShellChromeLayer(
    selectedTab: AppTab,
    showNavigation: Boolean,
    backdropLayer: GraphicsLayer,
    unreadNotifications: Int,
    unreadMessages: Int,
    accountSidebarWidthPx: Float,
    accountSidebarProgress: Float,
    bottomNavigationHeightPx: Float,
    navigationHideProgress: Float,
    onSelectTab: (AppTab) -> Unit,
    topicDropdownOpen: Boolean,
    activeTopic: String,
    onDismissTopicDropdown: () -> Unit,
    onSelectTopic: (String) -> Unit,
    exitHintVisible: Boolean,
    showComposeFab: Boolean,
    composeFabHideProgress: Float,
    onComposePost: () -> Unit,
) {
    if (showNavigation) {
        BottomNavigationBar(
            selectedTab = selectedTab,
            backdropLayer = backdropLayer,
            unreadNotifications = unreadNotifications,
            unreadMessages = unreadMessages,
            onSelectTab = onSelectTab,
            modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer {
                // The navbar exits vertically while the page itself moves under the account rail.
                translationX = accountSidebarWidthPx * accountSidebarProgress
                translationY = bottomNavigationHeightPx * accountSidebarProgress +
                    bottomNavigationHeightPx * navigationHideProgress
            },
        )
    }

    TopicDropdownOverlay(
        open = topicDropdownOpen,
        activeTopic = activeTopic,
        onDismiss = onDismissTopicDropdown,
        onSelect = onSelectTopic,
    )

    if (exitHintVisible) {
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                .clip(RoundedCornerShape(50)).background(Color(0xFF1B1914))
                .border(.7.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Tap again to exit", color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
        }
    }

    if (showComposeFab) {
        ComposePostFab(
            onClick = onComposePost,
            modifier = Modifier.align(Alignment.BottomEnd).graphicsLayer {
                translationX = accountSidebarWidthPx * accountSidebarProgress
                translationY = bottomNavigationHeightPx * composeFabHideProgress
                alpha = 1f - composeFabHideProgress
            },
        )
    }
}
