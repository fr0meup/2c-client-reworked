package com.twocents.mobile.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.common.AppBackgroundTasks
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.EdgeToEdgeDialogWindow
import com.twocents.mobile.ui.common.appBottomSheetDragHandle
import com.twocents.mobile.ui.compose.InlineDismissControl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class ThreadSheetEntry(val anchorUuid: String, val listState: LazyListState = LazyListState())

/** A single sheet with a page stack preserves each branch's exact scroll position on Back. */
@Composable
internal fun PostCommentThreadSheet(
    index: CommentThreadIndex,
    initialAnchorUuid: String,
    targetCommentUuid: String?,
    controller: PostDetailController,
    api: RpcApi,
    auth: AuthState,
    onDismiss: () -> Unit,
    visible: Boolean = true,
) {
    val state = controller.state
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val pages = remember(initialAnchorUuid) {
        mutableStateListOf<ThreadSheetEntry>().apply {
            val path = targetCommentUuid?.let(index::pathTo).orEmpty()
            (path.ifEmpty { listOf(initialAnchorUuid) }).forEach { add(ThreadSheetEntry(it)) }
        }
    }
    val entry = pages.last()
    val page = remember(index, entry.anchorUuid) { index.page(entry.anchorUuid) }
    var replyTarget by remember { mutableStateOf<PostComment?>(null) }
    var highlighted by remember { mutableStateOf(targetCommentUuid) }
    var initialTargetHandled by remember { mutableStateOf(false) }
    val offset = remember { Animatable(1f) }
    val dragOffset = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val replyDraft = remember { CommentComposerDraft() }
    val hasDraftContent = replyDraft.editor.value.text.isNotBlank() || replyDraft.imageUri.value != null
    var confirmingClose by remember { mutableStateOf(false) }
    LaunchedEffect(hasDraftContent) { if (!hasDraftContent) confirmingClose = false }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            offset.animateTo(1f, tween(200))
            onDismiss()
        }
    }
    fun back() {
        if (confirmingClose) {
            confirmingClose = false
        } else if (pages.size > 1) {
            pages.removeAt(pages.lastIndex)
            if (!hasDraftContent) replyTarget = null
        } else if (hasDraftContent) confirmingClose = true else close()
    }
    fun requestClose() {
        if (hasDraftContent) {
            confirmingClose = true
            scope.launch { dragOffset.animateTo(0f, tween(180)) }
        } else close()
    }
    LaunchedEffect(Unit) { offset.animateTo(0f, tween(210)) }
    LaunchedEffect(entry, page, targetCommentUuid) {
        if (!initialTargetHandled && targetCommentUuid != null) {
            val targetIndex = page.rows.indexOfFirst { it.comment.uuid == targetCommentUuid }
            if (targetIndex >= 0) {
                entry.listState.scrollToItem(targetIndex + if (page.ancestors.isEmpty()) 0 else 1)
                initialTargetHandled = true
                delay(2400)
                highlighted = null
            }
        }
    }
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(entry, replyTarget?.uuid, imeBottom, visible) {
        if (!visible) return@LaunchedEffect
        val target = replyTarget ?: return@LaunchedEffect
        val position = page.rows.indexOfFirst { it.comment.uuid == target.uuid }
        if (position >= 0 && imeBottom > 0) {
            delay(80)
            entry.listState.animateScrollToItem(position + if (page.ancestors.isEmpty()) 0 else 1, -with(density) { 18.dp.roundToPx() })
        }
    }
    // Profiles open in the shell above this screen. Hide only the dialog window;
    // retain the branch stack, list states, and shared composer draft until return.
    if (!visible) return
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow(navigationBarColor = android.graphics.Color.TRANSPARENT)
        BackHandler(onBack = ::back)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f * (1f - offset.value))).clickable { requestClose() })
            val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight * .92f)
                    .graphicsLayer { translationY = offset.value * (size.height + 40.dp.toPx()) + dragOffset.value }
                    .clip(shape).background(Color(0xFF141410))
                    .border(1.dp, Color.White.copy(alpha = .08f), shape)
                    .clickable(onClick = {}),
            ) {
                val handle = Modifier.appBottomSheetDragHandle(
                    onDrag = { amount -> scope.launch { dragOffset.snapTo((dragOffset.value + amount).coerceAtLeast(0f)) } },
                    onDragEnd = {
                        if (dragOffset.value > with(density) { 90.dp.toPx() }) requestClose()
                        else scope.launch { dragOffset.animateTo(0f, tween(180)) }
                    },
                    onDragCancel = { scope.launch { dragOffset.animateTo(0f, tween(180)) } },
                )
                Box(Modifier.fillMaxWidth().then(handle).padding(top = 9.dp, bottom = 5.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replies", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (pages.size > 1 && !confirmingClose) {
                        Box(Modifier.width(48.dp).height(44.dp).clickable { AppHaptics.open(view); back() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous replies", tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(20.dp))
                        }
                    } else {
                        InlineDismissControl(
                            expanded = confirmingClose,
                            onExpand = ::requestClose,
                            onConfirm = ::close,
                            onBoundsChanged = {},
                        )
                    }
                }
                LazyColumn(state = entry.listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (page.ancestors.isNotEmpty()) {
                        item(key = "parent-context") {
                            val parent = page.ancestors.single()
                            PostCommentThreadContext(parent, state.aliases[parent.authorUuid] ?: parent.author.alias)
                        }
                    }
                    items(page.rows, key = { it.comment.uuid }, contentType = { "comment-${it.depth}" }) { row ->
                        PostCommentRow(
                            row = row,
                            vote = state.commentVotes[row.comment.uuid] ?: 0,
                            alias = state.aliases[row.comment.authorUuid] ?: row.comment.author.alias,
                            onReply = { replyTarget = row.comment },
                            onVote = { direction -> AppBackgroundTasks.mutations.launch { controller.toggleCommentVote(row.comment.uuid, direction) } },
                            highlighted = row.comment.uuid == highlighted,
                            ownComment = row.comment.authorUuid == auth.userUuid,
                            isOriginalPoster = row.comment.authorUuid == state.post?.authorUuid,
                            onDelete = { AppBackgroundTasks.mutations.launch { controller.deleteComment(row.comment.uuid) } },
                            moreReplyCount = page.moreReplies[row.comment.uuid] ?: 0,
                            onMoreReplies = {
                                pages.add(ThreadSheetEntry(row.comment.uuid))
                                confirmingClose = false
                                if (!hasDraftContent) replyTarget = null
                            },
                        )
                    }
                    item(key = "bottom-space") { Spacer(Modifier.height(if (replyTarget == null) 8.dp else 36.dp)) }
                }
                CommentComposer(
                    controller, state.aliases, api, auth, replyTarget, { replyTarget = null }, Modifier.fillMaxWidth(),
                    draft = replyDraft,
                )
            }
        }
    }
}
