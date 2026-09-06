package com.twocents.mobile.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.PostDetailScreen
import com.twocents.mobile.ui.messages.RoomChatScreen
import com.twocents.mobile.ui.messages.RoomSummary
import com.twocents.mobile.ui.theme.Background

@Composable
internal fun RoomChatSlideHost(
    openedRoom: RoomSummary?,
    targetMessageUuid: String?,
    auth: AuthState,
    api: RpcApi,
    aliases: Map<String, String>,
    navigationEnabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayedRoom by remember { mutableStateOf<RoomSummary?>(null) }
    val horizontalOffset = remember { Animatable(0f) }
    val touchSink = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        LaunchedEffect(openedRoom, widthPx) {
            if (widthPx <= 0f) return@LaunchedEffect
            if (openedRoom != null) {
                displayedRoom = openedRoom
                horizontalOffset.snapTo(widthPx)
                withFrameNanos { }
                horizontalOffset.animateTo(0f, tween(180, easing = ShellOutCubic))
            } else if (displayedRoom != null) {
                horizontalOffset.animateTo(widthPx, tween(165, easing = ShellInCubic))
                displayedRoom = null
                horizontalOffset.snapTo(0f)
            }
        }
        displayedRoom?.let { room ->
            Box(
                Modifier.fillMaxSize().background(Background)
                    // The full-screen sink prevents taps in transparent chat gaps reaching the room list.
                    .clickable(interactionSource = touchSink, indication = null, onClick = {})
                    .graphicsLayer { translationX = horizontalOffset.value },
            ) {
                RoomChatScreen(
                    room = room,
                    auth = auth,
                    api = api,
                    aliases = aliases,
                    onBack = onClose,
                    modifier = Modifier.fillMaxSize(),
                    targetMessageUuid = targetMessageUuid,
                    navigationEnabled = navigationEnabled,
                )
            }
        }
    }
}

@Composable
internal fun PostDetailSlideHost(
    openedPost: OpenedPost?,
    auth: AuthState,
    api: RpcApi,
    onClose: () -> Unit,
    onOpenPost: (OpenedPost) -> Unit,
    onQuotePost: (FeedPost) -> Unit,
    onOpenMessages: () -> Unit,
    onNotificationsFallback: suspend () -> Unit,
    navigationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var displayedPost by remember { mutableStateOf<OpenedPost?>(null) }
    val horizontalOffset = remember { Animatable(0f) }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        LaunchedEffect(openedPost, widthPx) {
            if (widthPx <= 0f) return@LaunchedEffect
            if (openedPost != null) {
                val isNewPost = displayedPost?.post?.uuid != openedPost.post.uuid
                displayedPost = openedPost
                if (isNewPost) {
                    horizontalOffset.snapTo(widthPx)
                    withFrameNanos { }
                    horizontalOffset.animateTo(0f, tween(180, easing = ShellOutCubic))
                }
            } else if (displayedPost != null) {
                horizontalOffset.animateTo(widthPx, tween(165, easing = ShellInCubic))
                displayedPost = null
                horizontalOffset.snapTo(0f)
            }
        }

        displayedPost?.let { opened ->
            Box(
                Modifier.fillMaxSize().graphicsLayer { translationX = horizontalOffset.value }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                PostDetailScreen(
                    seedPost = opened.post,
                    sourceController = opened.sourceController,
                    auth = auth,
                    api = api,
                    onBack = onClose,
                    targetCommentUuid = opened.targetCommentUuid,
                    onOpenPost = { post -> onOpenPost(OpenedPost(post, opened.sourceController)) },
                    onQuotePost = onQuotePost,
                    onOpenMessages = onOpenMessages,
                    onNotificationsFallback = onNotificationsFallback,
                    navigationEnabled = navigationEnabled,
                )
            }
        }
    }
}
