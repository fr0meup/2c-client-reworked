package com.twocents.mobile.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.PostDetailScreen
import com.twocents.mobile.ui.feed.loadFeedPost
import com.twocents.mobile.ui.common.rememberPullToRefreshState
import com.twocents.mobile.ui.leaderboard.LeaderboardScreen
import com.twocents.mobile.ui.profile.EditProfileSheet
import com.twocents.mobile.ui.profile.ProfileAvailability
import com.twocents.mobile.ui.profile.UserProfileContent
import com.twocents.mobile.ui.settings.SettingsScreen
import com.twocents.mobile.ui.theme.Background
import kotlinx.coroutines.launch

@Composable
internal fun RetainedOverlayHost(
    stack: List<ShellOverlayEntry>,
    exitingId: Long?,
    auth: AuthState,
    api: RpcApi,
    feedController: FeedController,
    onPop: () -> Unit,
    onPushPost: (OpenedPost) -> Unit,
    onPushProfile: (String, ComposeAuthorProfile?) -> Unit,
    onQuotePost: (FeedPost) -> Unit,
    onOpenMessages: () -> Unit,
    onMessageUser: (String) -> Unit,
    onOpenSidebar: () -> Unit,
    profileSelected: Boolean,
    onPushSettings: () -> Unit,
    onOfflineChanged: (Boolean) -> Unit,
    onOpenFeedback: () -> Unit,
    onLogout: () -> Unit,
    onNotificationsFallback: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stack.isEmpty()) return
    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
        ),
    ) {
        stack.forEachIndexed { index, entry ->
            key(entry.id) {
                val top = index == stack.lastIndex
                if (entry is ShellOverlayEntry.Quotes) {
                    // Keep the list composed beneath destinations, but remove its
                    // dialog window so it cannot cover or intercept the new page.
                    com.twocents.mobile.ui.feed.PostQuotesDialog(
                        sourcePost = entry.post, authUuid = auth.userUuid,
                        controller = entry.controller, onQuotePost = onQuotePost,
                        onOpenMessages = onOpenMessages, visible = top,
                        onOpenPost = { onPushPost(OpenedPost(it, entry.controller)) },
                        onDismiss = onPop,
                    )
                } else {
                val offset = remember(entry.id) { Animatable(screenWidthPx) }
                LaunchedEffect(entry.id) { offset.animateTo(0f, tween(180, easing = ShellOutCubic)) }
                LaunchedEffect(exitingId) {
                    if (exitingId == entry.id) offset.animateTo(screenWidthPx, tween(165, easing = ShellInCubic))
                }
                Box(
                    Modifier.fillMaxSize().background(Background)
                        .graphicsLayer { alpha = 1f; translationX = offset.value }
                        .zIndex(index.toFloat())
                        .clickable(
                            enabled = top,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    when (entry) {
                        is ShellOverlayEntry.Quotes -> Unit // Hosted as a retained sheet above.
                        is ShellOverlayEntry.Profile -> {
                            var loadedProfile by remember(entry.id) { mutableStateOf(entry.seed) }
                            var scrollRequest by remember(entry.id) { mutableStateOf(0) }
                            val refresh = rememberPullToRefreshState()
                            val scope = rememberCoroutineScope()
                            var editOpen by remember(entry.id) { mutableStateOf(false) }
                            Column(Modifier.fillMaxSize().background(Background)) {
                                ProfilePageHeader(
                                    profile = loadedProfile,
                                    authUuid = entry.userUuid,
                                    isOwn = entry.userUuid == auth.userUuid,
                                    onBack = onPop,
                                    onNetworthClick = { scrollRequest++ },
                                    onEditProfile = { editOpen = true },
                                    onOpenSettings = onPushSettings,
                                    onMessage = { onMessageUser(entry.userUuid) },
                                    isFollowing = feedController.isFollowing(entry.userUuid),
                                    onToggleFollow = { scope.launch { feedController.toggleFollowing(entry.userUuid) } },
                                    onBlock = { scope.launch {
                                        val blocked = ProfileAvailability.blockedByMe[entry.userUuid] == true
                                        val ok = if (blocked) feedController.unblockAuthor(entry.userUuid) else feedController.blockAuthor(entry.userUuid)
                                        if (ok) ProfileAvailability.blockedByMe[entry.userUuid] = !blocked
                                    } },
                                    refreshing = refresh.isRefreshing,
                                    modifier = Modifier.statusBarsPadding(),
                                )
                                UserProfileContent(
                                    auth = auth,
                                    api = api,
                                    targetUuid = entry.userUuid,
                                    scrollToTopRequest = scrollRequest,
                                    refreshState = refresh,
                                    feedController = feedController,
                                    onProfileLoaded = { loadedProfile = it },
                                    onOpenPost = { onPushPost(OpenedPost(it, feedController)) },
                                    onOpenCommentPost = { postUuid, commentUuid ->
                                        scope.launch {
                                            loadFeedPost(api, auth, postUuid)?.let { onPushPost(OpenedPost(it, feedController, commentUuid)) }
                                        }
                                    },
                                    onOpenProfile = onPushProfile,
                                    onQuotePost = onQuotePost,
                                    onOpenMessages = onOpenMessages,
                                    onNotificationsFallback = onNotificationsFallback,
                                    navigationActive = top,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (editOpen) EditProfileSheet(auth, api, loadedProfile, { editOpen = false }) { loadedProfile = it }
                        }
                        is ShellOverlayEntry.Post -> PostDetailScreen(
                            seedPost = entry.opened.post,
                            sourceController = entry.opened.sourceController,
                            auth = auth,
                            api = api,
                            onBack = onPop,
                            targetCommentUuid = entry.opened.targetCommentUuid,
                            onOpenPost = { onPushPost(OpenedPost(it, entry.opened.sourceController)) },
                            onQuotePost = onQuotePost,
                            onOpenMessages = onOpenMessages,
                            onNotificationsFallback = onNotificationsFallback,
                            navigationEnabled = top,
                        )
                        is ShellOverlayEntry.Leaderboard -> LeaderboardScreen(
                            auth = auth,
                            api = api,
                            aliases = feedController.state.aliases,
                            onBack = onPop,
                            onOpenMe = onOpenSidebar,
                            onOpenProfile = onPushProfile,
                            profileSelected = profileSelected,
                        )
                        is ShellOverlayEntry.Settings -> SettingsScreen(
                            auth = auth,
                            api = api,
                            onBack = onPop,
                            onOpenProfile = onPushProfile,
                            onOpenFeedback = onOpenFeedback,
                            onOfflineChanged = onOfflineChanged,
                            onLogout = onLogout,
                        )
                    }
                }
                }
            }
        }
    }
}
