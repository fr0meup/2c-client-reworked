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

private val MenuGold = Color(0xFFC8A44D)

@Composable
internal fun PostQuotesDialog(
    sourcePost: FeedPost,
    authUuid: String,
    controller: FeedController,
    onQuotePost: ((FeedPost) -> Unit)?,
    onOpenMessages: (() -> Unit)?,
    onDismiss: () -> Unit,
    visible: Boolean,
    onOpenPost: (FeedPost) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var quotes by remember { mutableStateOf(emptyList<FeedPost>()) }
    val loadedInteractions = remember { mutableSetOf<String>() }
    val interactionSlots = remember { kotlinx.coroutines.sync.Semaphore(3) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val savedContent = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val dismissDistance = with(LocalDensity.current) { 1200.dp.toPx() }
    val motion = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            motion.animateTo(0f, tween(210))
            onDismiss()
        }
    }
    LaunchedEffect(Unit) { motion.animateTo(1f, tween(210)) }
    LaunchedEffect(sourcePost.uuid) { quotes = controller.loadQuotes(sourcePost.uuid); loading = false }
    if (!visible) return
    // Preserve expanded card state too: restoring only the list offset is not
    // enough if returning would otherwise collapse a tall quote card.
    savedContent.SaveableStateProvider("quote-content") {
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      val view = LocalView.current
      DisposableEffect(view) {
          val window = (view.parent as? DialogWindowProvider)?.window
          val oldNavigationColor = window?.navigationBarColor
          window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
          window?.setWindowAnimations(0)
          window?.navigationBarColor = android.graphics.Color.TRANSPARENT
          window?.isNavigationBarContrastEnforced = false
          window?.let { WindowInsetsControllerCompat(it, view).isAppearanceLightNavigationBars = false }
          onDispose {
              if (oldNavigationColor != null) window?.navigationBarColor = oldNavigationColor
          }
      }
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f * motion.value)).clickable(onClick = ::close)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.86f)
                .graphicsLayer { translationY = dismissDistance * (1f - motion.value) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF0F0E0A)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(onClick = {}).navigationBarsPadding(),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)))
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(MenuGold.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(PostMenuIcons.Quote, null, tint = MenuGold, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Quotes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(if (loading) "Loading quotes…" else "${quotes.size} ${if (quotes.size == 1) "quote post" else "quote posts"}", color = Color.White.copy(alpha = 0.45f), fontSize = 11.5.sp)
                }
                Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(30.dp).clip(CircleShape).clickable(onClick = ::close).padding(7.dp))
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = MenuGold, strokeWidth = 2.dp) }
                quotes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No quotes yet", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp) }
                else -> LazyColumn(state = listState) { items(quotes, key = { it.uuid }) { quote ->
                    LaunchedEffect(quote.uuid) {
                        if (quote.uuid !in loadedInteractions) {
                            interactionSlots.acquire()
                            try {
                                if (controller.loadQuoteInteractions(quote.uuid)) loadedInteractions.add(quote.uuid)
                            } finally { interactionSlots.release() }
                        }
                    }
                    FeedPostCard(
                        post = quote, authUuid = authUuid,
                        currentVote = controller.state.postVotes[quote.uuid] ?: 0,
                        alias = controller.state.aliases[quote.authorUuid] ?: quote.author.alias,
                        pollVote = controller.state.pollVotes[quote.uuid],
                        likertVote = controller.state.likertVotes[quote.uuid],
                        pickVote = controller.state.pickVotes[quote.uuid],
                        pollResults = controller.state.pollResults[quote.uuid],
                        likertResults = controller.state.likertResults[quote.uuid],
                        picksResult = controller.state.picksResults[quote.uuid], controller = controller,
                        onOpenPost = onOpenPost, parentScrolling = { listState.isScrollInProgress },
                        onQuotePost = onQuotePost, onOpenMessages = onOpenMessages,
                    )
                } }
            }
        }
      }
    }
    }
}
