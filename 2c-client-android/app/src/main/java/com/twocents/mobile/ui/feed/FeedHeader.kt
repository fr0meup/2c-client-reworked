package com.twocents.mobile.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.HapticIntent

private val HeaderOutCubic = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) * (1f - fraction) }
private val HeaderInCubic = Easing { fraction -> fraction * fraction * fraction }

@Composable
fun FeedHeader(
    activeTopic: String,
    onSelectTopic: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    topicDropdownOpen: Boolean,
    onToggleTopicDropdown: () -> Unit,
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    onAdvancedSearch: () -> Unit = {},
    profileSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchInput by remember(searchQuery) { mutableStateOf(searchQuery) }
    val topBarHeight = 57.dp
    val searchTransition = updateTransition(targetState = searchOpen, label = "feed-search")
    val searchProgress = searchTransition.animateFloat(
        transitionSpec = {
            if (targetState) tween(260, easing = HeaderOutCubic)
            else tween(210, easing = HeaderInCubic)
        },
        label = "feed-search-progress",
    ) { open -> if (open) 1f else 0f }

    LaunchedEffect(searchQuery) {
        if (!searchOpen) searchInput = searchQuery
    }

    BackHandler(enabled = searchOpen) {
        searchInput = ""
        onSearchChange("")
        searchOpen = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
            .background(Background)
            .drawBehind {
                drawLine(
                    color = if (topicDropdownOpen) Background else Color.White.copy(alpha = 0.06f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 1f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1f),
                    strokeWidth = 1f,
                )
            }
            .padding(start = 12.dp, end = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = searchProgress.value
                    alpha = 1f - progress
                    translationX = -12.dp.toPx() * progress
                },
        ) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                HeaderLogoButton(
                    onClick = {
                        onPressLogo()
                    },
                )
            }

            val topicInteractionSource = remember { MutableInteractionSource() }
            Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(min = 112.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                    .pressScale(rememberPressScale(topicInteractionSource))
                    .clickable(
                        enabled = !searchOpen,
                        interactionSource = topicInteractionSource,
                        indication = null,
                    ) { AppHaptics.open(view); onToggleTopicDropdown() }
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Text(
                    text = activeTopic,
                    color = Color.White,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                ChevronIcon(
                    expanded = topicDropdownOpen,
                    tint = if (topicDropdownOpen) Gold else Color.White.copy(alpha = 0.65f),
                )
            }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy((-6).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderActionButton(
                    imageRes = SEARCH_ICON_RES,
                    iconSize = 31.dp,
                    fallback = Icons.Default.Search,
                    contentDescription = "Search",
                    hapticIntent = HapticIntent.Open,
                    onClick = {
                        if (topicDropdownOpen) onToggleTopicDropdown()
                        searchOpen = true
                    },
                )
                HeaderActionButton(
                    imageRes = PROFILE_ICON_RES,
                    pressedImageRes = PROFILE_ICON_SELECTED_RES,
                    iconSize = 29.dp,
                    fallback = Icons.Default.Person,
                    contentDescription = "Profile",
                    selected = profileSelected,
                    hapticIntent = HapticIntent.Open,
                    onClick = onNavigateProfile,
                )
            }
        }

        if (searchOpen || searchTransition.currentState || searchTransition.targetState) {
            SearchBar(
                value = searchInput,
                onValueChange = { searchInput = it },
                onSubmit = { onSearchChange(searchInput.trim()) },
                onClose = {
                    searchInput = ""
                    onSearchChange("")
                    searchOpen = false
                },
                active = searchOpen,
                onAdvancedSearch = onAdvancedSearch,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val progress = searchProgress.value
                        alpha = progress
                        translationY = 2.dp.toPx()
                        translationX = 52.dp.toPx() * (1f - progress)
                        scaleX = 0.94f + 0.06f * progress
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    },
            )
        }

    }
}
