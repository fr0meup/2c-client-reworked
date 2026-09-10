package com.twocents.mobile.ui.feed

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.MentionSuggestions
import com.twocents.mobile.ui.common.MentionVisualTransformation
import com.twocents.mobile.ui.common.mentionContext
import com.twocents.mobile.ui.common.mentionMarkup
import com.twocents.mobile.ui.compose.GifPickerSheet
import com.twocents.mobile.ui.common.LinkifiedText
import kotlinx.coroutines.launch

private val DetailSurface = Color(0xFF141410)
private val DetailGold = Color(0xFFC8A44D)
private val DetailEmerald = Color(0xFF34D399)
private val DetailRose = Color(0xFFF43F5E)

@Composable
internal fun CommentComposer(
    controller: PostDetailController,
    aliases: Map<String, String>,
    api: RpcApi,
    auth: AuthState,
    replyTarget: PostComment?,
    onCancelReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var imagePreviewOpen by remember { mutableStateOf(false) }
    var gifPickerOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri?.toString()
    }
    val replyAuthorStub = remember(replyTarget) {
        replyTarget?.let { comment ->
            FeedPost(
                uuid = comment.uuid,
                createdAt = comment.createdAt,
                authorUuid = comment.authorUuid,
                upvoteCount = 0,
                commentCount = 0,
                viewCount = 0,
                title = "",
                text = "",
                topic = "",
                author = comment.author,
                meta = FeedPostMeta(),
                postType = 0,
            )
        }
    }

    LaunchedEffect(replyTarget?.uuid) {
        if (replyTarget != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(
        modifier = modifier
            .background(DetailSurface)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = .08f),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (replyTarget != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DetailGold.copy(alpha = 0.08f))
                    .border(1.dp, DetailGold.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Replying to", color = Color(0xFFDAB857), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                replyAuthorStub?.let { FeedNetworthPill(it, compact = true) }
                Text(
                    replyTarget.replyPreviewText(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.5.sp,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.Close,
                    "Cancel reply",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp).clickable(onClick = onCancelReply).padding(3.dp),
                )
            }
        }
        imageUri?.let { uri ->
            Box(Modifier.size(60.dp)) {
                AsyncImage(
                    uri,
                    "Preview selected image",
                    Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).clickable { imagePreviewOpen = true },
                    contentScale = ContentScale.Crop,
                )
                Icon(
                    Icons.Outlined.Close,
                    "Remove image",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable { imageUri = null }
                        .padding(4.dp),
                )
            }
        }
        val activeMention = mentionContext(text)
        MentionSuggestions(
            context = activeMention,
            aliases = aliases,
            onShown = {},
            onDismiss = {},
            onSelect = { uuid, alias ->
                activeMention?.let {
                    val candidate = text.replaceRange(it.start, text.length, mentionMarkup(alias, uuid))
                    if (candidate.length <= 1_000) text = candidate
                }
                focusRequester.requestFocus()
            },
            placeAbove = true,
            gap = 9.dp,
            api = api,
            auth = auth,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp, max = 144.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(start = 12.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(1_000) },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                cursorBrush = SolidColor(DetailGold),
                visualTransformation = MentionVisualTransformation,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).padding(vertical = 6.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(
                            if (replyTarget == null) "Add your twocents..." else "Write a reply...",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        )
                        inner()
                    }
                },
            )
            Box(Modifier.size(28.dp).clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Image, "Add image", tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(17.dp))
            }
            Box(Modifier.height(28.dp).clickable { keyboard?.hide(); gifPickerOpen = true }.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                Text("GIF", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            val canSend = text.isNotBlank() || imageUri != null
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canSend && !state.submitting) {
                        AppHaptics.confirm(view)
                        val submittedText = text
                        val submittedImage = imageUri
                        val submittedReply = replyTarget?.uuid
                        com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch {
                            if (controller.createComment(submittedText, submittedReply, submittedImage, context.applicationContext)) {
                                if (text == submittedText) text = ""
                                if (imageUri == submittedImage) imageUri = null
                                scope.launch { if (replyTarget?.uuid == submittedReply) onCancelReply() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(DetailGold.copy(alpha = if (canSend && !state.submitting) 1f else 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF0F0E0A), strokeWidth = 1.8.dp)
                    } else {
                        Icon(Icons.Rounded.Send, "Send", tint = Color(0xFF0F0E0A), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
    if (gifPickerOpen) {
        GifPickerSheet(
            onDismiss = { gifPickerOpen = false; focusRequester.requestFocus(); keyboard?.show() },
            onSelect = { url ->
                imageUri = url
                gifPickerOpen = false
                focusRequester.requestFocus()
                keyboard?.show()
            },
        )
    }
    if (imagePreviewOpen) {
        imageUri?.let { uri ->
            ImageLightbox(images = listOf(uri), initialIndex = 0, originRect = null) { imagePreviewOpen = false }
        } ?: run { imagePreviewOpen = false }
    }
}

@Composable
private fun CommentGifPicker(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("twocents-comment-gifs", Context.MODE_PRIVATE) }
    var saved by remember { mutableStateOf(preferences.getStringSet("urls", emptySet()).orEmpty().toList()) }
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DetailSurface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("GIFs", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp).clickable(onClick = onDismiss).padding(3.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .padding(start = 12.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(DetailGold),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (url.isBlank()) Text("Paste GIF URL", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                            inner()
                        }
                    },
                )
                Text(
                    "Use",
                    color = if (url.trim().startsWith("http")) DetailGold else Color.White.copy(alpha = 0.25f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = url.trim().startsWith("http")) {
                            val chosen = url.trim()
                            saved = (listOf(chosen) + saved).distinct().take(40)
                            preferences.edit().putStringSet("urls", saved.toSet()).apply()
                            onSelect(chosen)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (saved.isEmpty()) {
                Text("Paste a GIF URL to save it here.", color = Color.White.copy(alpha = 0.38f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(saved, key = { it }) { savedUrl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.035f))
                                .clickable { onSelect(savedUrl) }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            AsyncImage(savedUrl, null, Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Text(savedUrl, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
