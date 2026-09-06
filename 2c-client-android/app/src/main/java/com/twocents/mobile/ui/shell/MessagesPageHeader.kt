package com.twocents.mobile.ui.shell

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.outlined.Link
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
import com.twocents.mobile.ui.common.RefreshProgressBar
import com.twocents.mobile.notifications.NotificationFilter
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.profile.ProfileAvailability

private val OutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

@Composable
fun MessagesPageHeader(
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    profileSelected: Boolean = false,
    onExplore: () -> Unit,
    onJoinCode: (String) -> Unit,
    onCreateRoom: () -> Unit,
    busy: Boolean,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    var joinOpen by rememberSaveable { mutableStateOf(false) }
    var joinCode by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth().background(Background)) {
        StandardPageTopBar(
            title = "Rooms",
            onPressLogo = onPressLogo,
            onNavigateProfile = onNavigateProfile,
            profileSelected = profileSelected,
        )

        val roomTransition = updateTransition(targetState = joinOpen, label = "room-header")
        val roomProgress = roomTransition.animateFloat(
            transitionSpec = { tween(durationMillis = 280, easing = OutCubic) },
            label = "room-header-progress",
        ) { open -> if (open) 1f else 0f }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = roomProgress.value
                        alpha = (1f - progress * 1.35f).coerceIn(0f, 1f)
                        translationX = -28.dp.toPx() * progress
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderActionCard(
                    label = "Explore rooms",
                    icon = Icons.Outlined.Explore,
                    iconTint = Gold,
                    onClick = onExplore,
                    modifier = Modifier.weight(1f),
                )
                HeaderActionCard(
                    label = "New room",
                    icon = Icons.Outlined.Add,
                    iconTint = Color.White.copy(alpha = 0.9f),
                    onClick = { joinOpen = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (joinOpen || roomTransition.currentState || roomTransition.targetState) {
                RoomJoinInput(
                    value = joinCode,
                    onValueChange = { joinCode = it },
                    onClose = {
                        joinCode = ""
                        joinOpen = false
                    },
                    active = joinOpen,
                    busy = busy,
                    onSubmit = {
                        if (joinCode.isBlank()) onCreateRoom() else onJoinCode(joinCode.trim())
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = roomProgress.value
                            alpha = ((progress - 0.22f) / 0.78f).coerceIn(0f, 1f)
                            translationX = 72.dp.toPx() * (1f - progress)
                            scaleX = 0.9f + 0.1f * progress
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) { RefreshProgressBar(active = refreshing) }
    }
}

@Composable
private fun RoomJoinInput(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    active: Boolean,
    busy: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF14120E).copy(alpha = 0.96f))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(15.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Link,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(15.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .padding(horizontal = 9.dp),
            singleLine = true,
            enabled = active,
            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
            cursorBrush = SolidColor(Gold),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("Paste invite link...", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
        RoomSubmitButton(hasCode = value.isNotBlank(), busy = busy, onClick = onSubmit)
        CircleIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Close room input",
            onClick = onClose,
            enabled = active,
            size = 32.dp,
            iconSize = 15.dp,
            background = Color.White.copy(alpha = 0.06f),
            borderColor = Color.Transparent,
            hapticIntent = null,
        )
    }
}

@Composable
private fun RoomSubmitButton(hasCode: Boolean, busy: Boolean, onClick: () -> Unit) {
    val background = if (hasCode) Gold else Gold.copy(alpha = 0.12f)
    val foreground = if (hasCode) Color(0xFF0F0E0A) else Gold
    Row(
        modifier = Modifier
            .height(28.dp)
            .padding(end = 6.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, if (hasCode) Color.Transparent else Gold.copy(alpha = 0.3f), CircleShape)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!hasCode && !busy) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = foreground, modifier = Modifier.size(13.dp))
        }
        Text(
            text = if (busy) "Working…" else if (hasCode) "Join" else "Create",
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        if (hasCode) {
            Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = foreground, modifier = Modifier.size(13.dp))
        }
    }
}
