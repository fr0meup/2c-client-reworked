package com.twocents.mobile.ui.shell
import com.twocents.mobile.ui.common.AppDropdown

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.HapticIntent
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.feed.HeaderActionButton
import com.twocents.mobile.ui.feed.HeaderLogoButton
import com.twocents.mobile.ui.feed.PROFILE_ICON_RES
import com.twocents.mobile.ui.feed.PROFILE_ICON_SELECTED_RES
import com.twocents.mobile.ui.feed.PostMenuIcons
import com.twocents.mobile.core.platform.ShareActions
import com.twocents.mobile.ui.common.RefreshProgressBar
import com.twocents.mobile.notifications.NotificationFilter
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.profile.ProfileAvailability

private val OutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

@Composable
fun ProfilePageHeader(
    profile: ComposeAuthorProfile?,
    authUuid: String,
    isOwn: Boolean,
    onBack: () -> Unit,
    onNetworthClick: () -> Unit,
    isFollowing: Boolean = false,
    onToggleFollow: (String?) -> Unit = {},
    onMessage: () -> Unit = {},
    onBlock: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var actionsOpen by remember { mutableStateOf(false) }
    var aliasEntry by remember { mutableStateOf(false) }
    var aliasText by remember { mutableStateOf("") }
    var confirmUnfollow by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val blockedByMe = ProfileAvailability.blockedByMe[authUuid] == true
    Box(modifier.fillMaxWidth().height(52.dp).background(Background)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawLine(HeaderBorder, androidx.compose.ui.geometry.Offset(0f, size.height - 1f), androidx.compose.ui.geometry.Offset(size.width, size.height - 1f), 1f)
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (isOwn) {
                    CircleIconButton(Icons.Outlined.ArrowBack, "Back", onBack, 38.dp, 18.dp)
                } else {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White.copy(alpha = .78f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNetworthClick,
                    ),
            ) {
                ComposeNetworthPill(profile = profile, authUuid = authUuid)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (isOwn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(Icons.Outlined.Edit, "Edit profile", onEditProfile, 38.dp, 16.dp, hapticIntent = HapticIntent.Open)
                        CircleIconButton(Icons.Outlined.Settings, "Settings", onOpenSettings, 38.dp, 16.dp, hapticIntent = HapticIntent.Open)
                    }
                } else {
                    Box {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).clickable { AppHaptics.open(view); actionsOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.MoreHoriz, "Profile actions", tint = Color.White.copy(alpha = .78f), modifier = Modifier.size(20.dp))
                        }
                        AppDropdown(
                            expanded = actionsOpen,
                            onDismissRequest = { actionsOpen = false; confirmUnfollow = false; confirmBlock = false },
                            modifier = Modifier.width(202.dp),
                            shape = RoundedCornerShape(13.dp),
                            containerColor = Color(0xFF141410),
                            tonalElevation = 0.dp,
                            shadowElevation = 24.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
                            verticalTrim = 3.dp,
                        ) {
                            if (aliasEntry && !isFollowing) {
                                Column(Modifier.width(236.dp).padding(10.dp)) {
                                    Text("Choose an alias", color = Color.White.copy(alpha = .82f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    Text("This is how this person will appear to you.", color = Color.White.copy(alpha = .35f), fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                                    BasicTextField(
                                        value = aliasText,
                                        onValueChange = { aliasText = it.take(30) },
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White.copy(alpha = .9f), fontSize = 13.sp),
                                        cursorBrush = SolidColor(Gold),
                                        modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(11.dp)).background(Color.Black.copy(alpha = .2f))
                                            .border(.7.dp, Gold.copy(alpha = .28f), RoundedCornerShape(11.dp)).padding(horizontal = 11.dp, vertical = 9.dp),
                                        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (aliasText.isBlank()) Text("Nickname", color = Color.White.copy(alpha = .22f), fontSize = 12.5.sp); inner() } },
                                    )
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                                        Box(Modifier.size(40.dp).clickable { aliasEntry = false; aliasText = "" }, contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Close, "Cancel alias", tint = Color.White.copy(alpha = .48f), modifier = Modifier.size(17.dp))
                                        }
                                        Box(Modifier.padding(start = 5.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = .12f)).clickable(enabled = aliasText.isNotBlank()) { AppHaptics.confirm(view); actionsOpen = false; aliasEntry = false; onToggleFollow(aliasText.trim()) }.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                                            Text("Follow", color = Gold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                            ProfileMenuItem(
                                icon = if (isFollowing) PostMenuIcons.UserCheck else PostMenuIcons.UserPlus,
                                label = if (confirmUnfollow) "Are you sure?" else if (isFollowing) "Unfollow" else "Follow",
                                iconTint = if (isFollowing) Gold else Color.White.copy(alpha = .42f),
                                iconOffsetX = 1.5.dp,
                            ) {
                                when {
                                    !isFollowing -> { AppHaptics.open(view); aliasEntry = true; confirmUnfollow = false }
                                    !confirmUnfollow -> { AppHaptics.open(view); confirmUnfollow = true }
                                    else -> { AppHaptics.confirm(view); actionsOpen = false; confirmUnfollow = false; onToggleFollow(null) }
                                }
                            }
                            ProfileMenuItem(PostMenuIcons.MessageSquare, "Message") { actionsOpen = false; onMessage() }
                            HorizontalDivider(color = Color.White.copy(alpha = .06f))
                            ProfileMenuItem(PostMenuIcons.Link2, "Copy profile link") {
                                ShareActions.copyText(context, "2c profile", "https://twocents.money/user/$authUuid")
                                actionsOpen = false
                            }
                            ProfileMenuItem(PostMenuIcons.Copy, "Copy UUID") {
                                ShareActions.copyText(context, "2c user UUID", authUuid)
                                actionsOpen = false
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = .06f))
                            ProfileMenuItem(PostMenuIcons.Ban, if (!blockedByMe && confirmBlock) "Are you sure?" else if (blockedByMe) "Unblock" else "Block", destructive = !blockedByMe, iconTint = if (blockedByMe) Gold else Color(0xFFFB7185)) {
                                if (!blockedByMe && !confirmBlock) confirmBlock = true else {
                                    actionsOpen = false
                                    confirmBlock = false
                                    onBlock()
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
        RefreshProgressBar(active = refreshing, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    iconTint: Color = if (destructive) Color(0xFFFB7185) else Color.White.copy(alpha = .42f),
    iconOffsetX: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) Color(0xFFFB7185) else Color.White.copy(alpha = .82f), fontSize = 13.sp) },
        onClick = onClick,
        leadingIcon = {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.offset(x = iconOffsetX).size(16.dp))
            }
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp),
        modifier = Modifier.size(width = 202.dp, height = 38.dp),
    )
}
