package com.twocents.mobile.ui.shell

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.messages.RoomSummary

/**
 * Draws the account sidebar and its closed-state edge gesture. Navigation and
 * data mutations remain callbacks so MainShell continues to own all app state.
 */
@Composable
internal fun BoxScope.MainShellSidebarLayer(
    accountSidebarOpen: Boolean,
    navigationActive: Boolean,
    feedHeaderHeight: Dp,
    bottomNavigationHeight: Dp,
    accountSidebarWidthPx: Float,
    accountSidebarVisualProgress: Float,
    accountSidebarEdgeProgress: Float,
    onEdgeProgressChanged: (Float) -> Unit,
    onEdgeHoldingChanged: (Boolean) -> Unit,
    onOpenChanged: (Boolean) -> Unit,
    onDragFractionChange: (Float) -> Unit,
    profile: ComposeAuthorProfile?,
    auth: AuthState,
    api: RpcApi,
    alias: String?,
    notificationRevision: Int,
    recentRooms: List<RoomSummary>,
    onOpenProfile: () -> Unit,
    onOpenActivity: (String, String?) -> Unit,
    onOpenRoom: (RoomSummary) -> Unit,
    onOpenRoomsOverview: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenSettings: () -> Unit,
    onOfflineModeChanged: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    if (!accountSidebarOpen) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(top = feedHeaderHeight, bottom = bottomNavigationHeight)
                .width(28.dp)
                .fillMaxHeight()
                .zIndex(299f)
                .pointerInput(accountSidebarWidthPx) {
                    // Keep the drag's progress local. Waiting for Compose state to
                    // round-trip on every pointer event caused the edge to twitch.
                    var gestureProgress = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            gestureProgress = 0f
                            onEdgeProgressChanged(0f)
                            onEdgeHoldingChanged(true)
                        },
                        onDragEnd = {
                            if (gestureProgress >= .14f) onOpenChanged(true)
                            else {
                                onEdgeHoldingChanged(false)
                                onEdgeProgressChanged(0f)
                            }
                        },
                        onDragCancel = {
                            onEdgeHoldingChanged(false)
                            onEdgeProgressChanged(0f)
                        },
                    ) { change, amount ->
                        if (amount < 0f || gestureProgress > 0f) {
                            change.consume()
                            gestureProgress = (gestureProgress - amount / accountSidebarWidthPx).coerceIn(0f, 1f)
                            onEdgeProgressChanged(gestureProgress)
                        }
                    }
                },
        )
    }
    AccountSidebar(
        modifier = Modifier
            .zIndex(300f)
            .graphicsLayer { translationX = accountSidebarWidthPx * accountSidebarVisualProgress },
        progress = accountSidebarVisualProgress,
        navigationActive = navigationActive,
        profile = profile,
        auth = auth,
        api = api,
        alias = alias,
        onDismiss = {
            onOpenChanged(false)
            onEdgeHoldingChanged(false)
            onEdgeProgressChanged(0f)
        },
        onOpenProfile = onOpenProfile,
        onDragFractionChange = onDragFractionChange,
        notificationRevision = notificationRevision,
        recentRooms = recentRooms,
        onOpenActivity = onOpenActivity,
        onOpenRoom = onOpenRoom,
        onOpenRoomsOverview = onOpenRoomsOverview,
        onOpenLeaderboard = onOpenLeaderboard,
        onOpenSettings = onOpenSettings,
        onOfflineModeChanged = onOfflineModeChanged,
        onLogout = onLogout,
    )
}
