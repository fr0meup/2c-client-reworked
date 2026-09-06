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
fun TransactionsPageHeader(
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    profileSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    StandardPageTopBar(
        title = "Transactions",
        onPressLogo = onPressLogo,
        onNavigateProfile = onNavigateProfile,
        profileSelected = profileSelected,
        modifier = modifier,
    )
}

@Composable
fun BookmarksPageHeader(
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    profileSelected: Boolean = false,
    refreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Background)) {
        StandardPageTopBar(
            title = "Bookmarks",
            onPressLogo = onPressLogo,
            onNavigateProfile = onNavigateProfile,
            profileSelected = profileSelected,
        )
        Box(Modifier.fillMaxWidth().height(2.dp)) { RefreshProgressBar(active = refreshing) }
    }
}

