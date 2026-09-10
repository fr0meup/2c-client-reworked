package com.twocents.mobile.ui.feed
import com.twocents.mobile.ui.common.AppDropdown

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import com.twocents.mobile.ui.messages.RoomNavigationBus
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.AppToast
import com.twocents.mobile.core.platform.ShareActions

private val MenuSurface = Color(0xFF141410)
private val MenuGold = Color(0xFFC8A44D)

@Composable
internal fun PostOptionsButton(
    post: FeedPost,
    authUuid: String,
    controller: FeedController,
    onQuotePost: ((FeedPost) -> Unit)?,
    onDeleted: (() -> Unit)? = null,
    onOpenMessages: (() -> Unit)? = null,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    iconTint: Color = Color.White.copy(alpha = 0.45f),
    buttonOffsetX: Dp = 0.dp,
    menuOffsetX: Dp = 0.dp,
    adaptiveMiddleOffset: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.09f else 0f, tween(if (pressed) 70 else 240), label = "menu-press")
    var expanded by remember(post.uuid) { mutableStateOf(false) }
    var confirmDelete by remember(post.uuid) { mutableStateOf(false) }
    var confirmUnfollow by remember(post.uuid) { mutableStateOf(false) }
    var confirmBlock by remember(post.uuid) { mutableStateOf(false) }
    var confirmMute by remember(post.uuid) { mutableStateOf(false) }
    val ownPost = post.authorUuid == authUuid
    val following = controller.isFollowing(post.authorUuid)
    val muted = controller.isMuted(post.authorUuid)
    val bookmarked = controller.isBookmarked(post.uuid)
    val density = LocalDensity.current
    val view = LocalView.current
    var anchorTopPx by remember(post.uuid) { mutableFloatStateOf(0f) }
    var anchorBottomPx by remember(post.uuid) { mutableFloatStateOf(0f) }
    val estimatedMenuHeight = if (ownPost) 225.dp else 380.dp
    val needsMiddleOffset = adaptiveMiddleOffset && with(density) {
        val menuPx = estimatedMenuHeight.toPx()
        anchorTopPx < menuPx && (view.height - anchorBottomPx) < menuPx
    }

    fun feedback(message: String) {
        val failed = message.contains("fail", true) || message.contains("couldn't", true)
        if (failed) AppToast.error(message) else AppToast.success(message)
    }

    val hitSize = buttonSize
    Box(Modifier.offset(x = buttonOffsetX).size(hitSize).onGloballyPositioned {
        val bounds = it.boundsInWindow()
        anchorTopPx = bounds.top
        anchorBottomPx = bounds.bottom
    }) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = pressAlpha))
                .clickable(interactionSource = interaction, indication = null) {
                    AppHaptics.open(view)
                    expanded = !expanded
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.MoreHoriz, "Post options", tint = iconTint, modifier = Modifier.size(iconSize)) }

        AppDropdown(
            expanded = expanded, onDismissRequest = { expanded = false; confirmDelete = false; confirmUnfollow = false; confirmBlock = false; confirmMute = false }, modifier = Modifier.width(194.dp),
            offset = DpOffset(x = if (needsMiddleOffset) (-206).dp else menuOffsetX, y = 3.dp),
            shape = RoundedCornerShape(13.dp), containerColor = MenuSurface, tonalElevation = 0.dp, shadowElevation = 24.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            verticalTrim = 3.dp,
        ) {
            PostMenuItem(PostMenuIcons.Link2, "Copy link") {
                ShareActions.copyText(context, "2C post", "https://twocents.money/post/${post.uuid}")
                expanded = false; feedback("Link copied")
            }
            PostMenuItem(PostMenuIcons.Copy, "Copy text") {
                val copyText = listOf(post.title.trim(), prepareFeedPostContent(post).visibleText.trim())
                    .filter(String::isNotBlank).joinToString("\n\n")
                ShareActions.copyText(context, "2C post text", copyText)
                expanded = false; feedback("Text copied")
            }
            onQuotePost?.let { quote -> PostMenuItem(PostMenuIcons.Quote, "Quote post") { expanded = false; quote(post) } }
            PostMenuItem(PostMenuIcons.Bookmark, if (bookmarked) "Remove bookmark" else "Bookmark") {
                expanded = false
                com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch {
                    when (controller.toggleBookmark(post.uuid)) {
                        true -> feedback("Post bookmarked")
                        false -> feedback("Bookmark removed")
                        null -> feedback("Bookmark failed")
                    }
                }
            }
            PostMenuItem(PostMenuIcons.Quote, "View quotes") { expanded = false; QuotesNavigationBus.open(post, controller) }
            if (!ownPost) {
                PostMenuDivider()
                PostMenuItem(
                    if (following) PostMenuIcons.UserCheck else PostMenuIcons.UserPlus,
                    if (following && confirmUnfollow) "Are you sure?" else if (following) "Unfollow" else "Follow",
                    iconTint = if (following) MenuGold else null,
                ) {
                    if (following && !confirmUnfollow) {
                        AppHaptics.open(view)
                        confirmUnfollow = true
                    } else if (following) {
                        AppHaptics.confirm(view)
                        expanded = false
                        confirmUnfollow = false
                        com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { feedback(if (controller.toggleFollowing(post.authorUuid)) "Following updated" else "Follow failed") }
                    } else {
                        AppHaptics.open(view)
                        expanded = false
                        confirmUnfollow = false
                        ProfileNavigationBus.openForFollow(post.authorUuid)
                    }
                }
                PostMenuItem(PostMenuIcons.Mail, "Message") {
                    expanded = false
                    scope.launch {
                        val room = controller.startDirectMessage(post.authorUuid)
                        if (room != null) {
                            RoomNavigationBus.open(room)
                        } else feedback("Couldn't start DM")
                    }
                }
                PostMenuItem(
                    PostMenuIcons.VolumeX,
                    if (!muted && confirmMute) "Are you sure?" else if (muted) "Unmute posts" else "Mute posts",
                    iconTint = if (muted) MenuGold else null,
                ) {
                    if (!muted && !confirmMute) {
                        AppHaptics.open(view)
                        confirmMute = true
                    } else {
                        AppHaptics.confirm(view)
                        expanded = false
                        confirmMute = false
                        when (controller.toggleMuted(post.authorUuid)) {
                            true -> feedback("Posts from this user are now muted")
                            false -> feedback("User unmuted")
                            null -> feedback("Couldn't update mute setting")
                        }
                    }
                }
                PostMenuDivider()
                PostMenuItem(PostMenuIcons.Ban, if (confirmBlock) "Are you sure?" else "Block", destructive = true) {
                    if (!confirmBlock) { AppHaptics.open(view); confirmBlock = true } else {
                        AppHaptics.confirm(view)
                        expanded = false
                        confirmBlock = false
                        com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { feedback(if (controller.blockAuthor(post.authorUuid)) "User blocked" else "Block failed") }
                    }
                }
            } else {
                PostMenuDivider()
                PostMenuItem(PostMenuIcons.Trash2, if (confirmDelete) "Are you sure?" else "Delete", destructive = true) {
                    if (!confirmDelete) { AppHaptics.open(view); confirmDelete = true } else {
                        AppHaptics.confirm(view)
                        expanded = false
                        confirmDelete = false
                        com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch { if (controller.deletePost(post.uuid)) { feedback("Post deleted"); scope.launch { onDeleted?.invoke() } } else feedback("Delete failed") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostMenuItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) Color(0xFFFB7185) else Color.White.copy(alpha = 0.82f), fontSize = 13.sp) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = iconTint ?: if (destructive) Color(0xFFFB7185) else Color.White.copy(alpha = 0.42f), modifier = Modifier.size(16.dp)) },
        contentPadding = PaddingValues(horizontal = 11.dp), modifier = Modifier.size(width = 194.dp, height = 38.dp),
    )
}

@Composable
private fun PostMenuDivider() = Box(Modifier.width(194.dp).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
