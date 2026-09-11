package com.twocents.mobile.ui.compose

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.FeedQuoteCard
import com.twocents.mobile.ui.common.MentionSuggestions
import com.twocents.mobile.ui.common.AppToast
import com.twocents.mobile.ui.common.mentionContext
import com.twocents.mobile.ui.common.mentionMarkup
import com.twocents.mobile.ui.common.appBottomSheetDragHandle
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
private val ComposeModalSurface = Color(0xFF141410)
private const val SHEET_OPEN_MS = 220
private const val SHEET_CLOSE_MS = SHEET_OPEN_MS

private val OutCubic = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) * (1f - fraction) }
private val InCubic = Easing { fraction -> fraction * fraction * fraction }

@Composable
fun ComposePostModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    profile: ComposeAuthorProfile? = null,
    quotedPost: FeedPost? = null,
    initialTopic: String = "Lounge",
    mentionAliases: Map<String, String> = emptyMap(),
    mentionApi: RpcApi? = null,
    mentionAuth: AuthState? = null,
    onPost: suspend (ComposePostDraft) -> Boolean = { true },
) {
    var mounted by remember { mutableStateOf(visible) }
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val bodyScrollState = rememberScrollState()
    val bodyBringIntoViewRequester = remember { BringIntoViewRequester() }
    var bodyTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var bodyFocused by remember { mutableStateOf(false) }
    var gifPickerOpen by remember { mutableStateOf(false) }
    var draftsOpen by remember { mutableStateOf(false) }
    val bodyFocusRequester = remember { FocusRequester() }
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    val latestOnPost = rememberUpdatedState(onPost)
    var modalAnimationJob by remember { mutableStateOf<Job?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val draftStore = remember(context) { ComposeDraftStore(context.applicationContext) }

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(TextFieldValue()) }
    var styleRanges by remember { mutableStateOf(emptyList<ComposeStyleRange>()) }
    var composeSession by remember { mutableIntStateOf(0) }
    var topic by remember { mutableStateOf("Lounge") }
    var pinnedTopic by remember { mutableStateOf("Lounge") }
    var activeOption by remember { mutableStateOf<ComposePostOption?>(null) }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }
    var pollLink by remember { mutableStateOf("") }
    var mediaUris by remember { mutableStateOf(emptyList<String>()) }
    var draftQuotedPost by remember { mutableStateOf(quotedPost) }
    var optionsOpen by remember { mutableStateOf(false) }
    var topicOpen by remember { mutableStateOf(false) }
    var savedFeedback by remember { mutableStateOf(false) }
    var draftsCount by remember { mutableIntStateOf(0) }
    var storedDrafts by remember { mutableStateOf(emptyList<StoredComposeDraft>()) }
    var loadedDraftId by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var boldActive by remember { mutableStateOf(false) }
    var italicActive by remember { mutableStateOf(false) }
    var bulletsActive by remember { mutableStateOf(false) }
    var quoteActive by remember { mutableStateOf(false) }
    var dismissConfirmationVisible by remember { mutableStateOf(false) }
    var dismissControlBounds by remember { mutableStateOf(Rect.Zero) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragAnimation = remember { Animatable(0f) }

    fun updateBody(next: TextFieldValue) {
        val limited = if (next.text.length <= 5_000) next else next.copy(
            text = next.text.take(5_000),
            selection = TextRange(next.selection.start.coerceAtMost(5_000), next.selection.end.coerceAtMost(5_000)),
        )
        val result = mutateComposeBody(body, limited, styleRanges, boldActive, italicActive, bulletsActive, quoteActive)
        body = result.value
        styleRanges = result.ranges
        boldActive = result.bold
        italicActive = result.italic
        bulletsActive = result.bullets
        quoteActive = result.quote
    }

    fun toggleMark(mark: ComposeMark) {
        val selection = body.selection
        if (selection.collapsed) {
            if (mark == ComposeMark.Bold) boldActive = !boldActive else italicActive = !italicActive
            return
        }
        val (_, nextRanges) = body.toggleComposeMark(mark, styleRanges)
        styleRanges = nextRanges
        if (mark == ComposeMark.Bold) boldActive = !boldActive else italicActive = !italicActive
    }

    fun closeModal() {
        if (closing) return
        closing = true
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        modalAnimationJob?.cancel()
        modalAnimationJob = scope.launch {
            progress.animateTo(0f, tween(SHEET_CLOSE_MS, easing = InCubic))
            mounted = false
            latestOnDismiss.value()
        }
    }

    fun settleDragBack() {
        val start = dragOffset
        dragOffset = 0f
        scope.launch {
            dragAnimation.snapTo(start)
            dragAnimation.animateTo(0f, tween(180, easing = OutCubic))
        }
    }

    LaunchedEffect(visible) {
        composeSession++
        if (visible) {
            modalAnimationJob?.cancel()
            mounted = true
            closing = false
            title = ""
            body = TextFieldValue()
            styleRanges = emptyList()
            topic = initialTopic
            pinnedTopic = initialTopic
            activeOption = null
            pollOptions = listOf("", "")
            pollLink = ""
            mediaUris = emptyList()
            draftQuotedPost = quotedPost
            loadedDraftId = null
            optionsOpen = false
            topicOpen = false
            isSubmitting = false
            boldActive = false
            italicActive = false
            bulletsActive = false
            quoteActive = false
            dismissConfirmationVisible = false
            dragOffset = 0f
            scope.launch { bodyScrollState.scrollTo(0) }
            scope.launch {
                storedDrafts = draftStore.load()
                draftsCount = storedDrafts.size
            }
            progress.stop()
            progress.snapTo(0f)
            // Let Compose commit the mounted, off-screen state before beginning timing.
            // Without this frame the first draw can already contain the final transform.
            withFrameNanos { }
            progress.animateTo(1f, tween(SHEET_OPEN_MS, easing = OutCubic))
        } else if (mounted && !closing) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            modalAnimationJob?.cancel()
            modalAnimationJob = scope.launch {
                progress.animateTo(0f, tween(SHEET_CLOSE_MS, easing = InCubic))
                mounted = false
                latestOnDismiss.value()
            }
        }
    }

    // Keep the editor tree composed behind the shell. Opening now only toggles
    // interaction and a graphics-layer translation instead of rebuilding the
    // complete editor, toolbar, menus, and activity-result launcher.
    val hasDraftContent = title.isNotBlank() ||
        body.text.isNotBlank() ||
        mediaUris.isNotEmpty() ||
        activeOption != null ||
        draftQuotedPost != null

    BackHandler(enabled = mounted) {
        if (dismissConfirmationVisible) {
            dismissConfirmationVisible = false
        } else if (!hasDraftContent) {
            closeModal()
        } else {
            optionsOpen = false
            topicOpen = false
            dismissConfirmationVisible = true
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        resolveComposeMediaSelection(context, uris, mediaUris, activeOption)?.let { selection ->
            mediaUris = selection.uris
            activeOption = selection.option
            if (selection.clearPollLink) pollLink = ""
        }
        optionsOpen = false
    }

    val hasSelection = body.selection.start != body.selection.end
    val canPost = hasDraftContent
    val hasVideoSelection = remember(mediaUris) {
        mediaUris.any { rawUri ->
            context.contentResolver.getType(android.net.Uri.parse(rawUri))?.startsWith("video/") == true
        }
    }
    val density = LocalDensity.current
    val dragCloseThreshold = with(density) { 60.dp.toPx() }
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val keyboardBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val keyboardVisible = keyboardBottom > 0.dp
    val defaultBottomPadding = maxOf(navigationBottom + 10.dp, 24.dp)
    val toolbarBottomPadding = if (keyboardVisible) 8.dp else defaultBottomPadding
    val menuBottomPadding = toolbarBottomPadding + 48.dp
    val sheetInteraction = remember { MutableInteractionSource() }
    val backdropInteraction = remember { MutableInteractionSource() }

    fun saveCurrentDraft() {
        scope.launch {
            val saved = draftStore.save(
                ComposePostDraft(
                    title = title.trim(), body = composeBodyToMarkdown(body.text, styleRanges).trim(),
                    topic = topic, option = activeOption, pollOptions = pollOptions,
                    mediaUris = mediaUris, pollLink = pollLink, quotedPost = draftQuotedPost,
                ),
                loadedDraftId,
            )
            storedDrafts = saved
            draftsCount = saved.size
            loadedDraftId = saved.firstOrNull()?.id
            savedFeedback = true
            delay(1500)
            savedFeedback = false
        }
    }

    fun submitCurrentPost() {
        if (isSubmitting) return
        isSubmitting = true
        val submittedSession = composeSession
        val submittedDraftId = loadedDraftId
        com.twocents.mobile.ui.common.AppBackgroundTasks.mutations.launch {
            val outcome = runCatching {
                latestOnPost.value(
                    ComposePostDraft(
                        title = title.trim(),
                        body = composeBodyToMarkdown(body.text, styleRanges).trim().take(5_000).ifBlank { "\u200B" },
                        topic = topic, option = activeOption, pollOptions = pollOptions,
                        mediaUris = mediaUris, pollLink = pollLink, quotedPost = draftQuotedPost,
                    ),
                )
            }
            val success = outcome.getOrDefault(false)
            if (composeSession == submittedSession) isSubmitting = false
            if (success) {
                AppToast.success("Post published")
                submittedDraftId?.let { draftId ->
                    storedDrafts = draftStore.delete(draftId)
                    draftsCount = storedDrafts.size
                    if (loadedDraftId == draftId) loadedDraftId = null
                }
                scope.launch { if (composeSession == submittedSession) closeModal() }
            } else AppToast.error(outcome.exceptionOrNull()?.let {
                com.twocents.mobile.ui.common.friendlyError(it, "Couldn't publish post")
            } ?: "Couldn't publish post")
        }
    }

    LaunchedEffect(body.text, body.selection, bodyFocused, keyboardVisible) {
        withFrameNanos { }
        if (!bodyFocused) return@LaunchedEffect
        val layout = bodyTextLayout ?: return@LaunchedEffect
        val cursorOffset = body.selection.end.coerceIn(0, layout.layoutInput.text.text.length)
        withFrameNanos { }
        // Relocate the caret itself. Scrolling to the content maximum also
        // reveals poll/media cards and leaves the line being edited obscured.
        val caret = layout.getCursorRect(cursorOffset)
        val extraBottom = with(density) { 34.dp.toPx() }
        bodyBringIntoViewRequester.bringIntoView(
            androidx.compose.ui.geometry.Rect(caret.left, caret.top, caret.right, caret.bottom + extraBottom),
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(mounted, dismissConfirmationVisible, dismissControlBounds) {
                if (!mounted || !dismissConfirmationVisible) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    if (!dismissControlBounds.contains(down.position)) {
                        dismissConfirmationVisible = false
                    }
                }
            }
            .then(if (mounted) Modifier else Modifier.clearAndSetSemantics { })
            .zIndex(if (mounted) 1000f else -1f),
    ) {
        val sheetHeight = maxHeight * 0.92f
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.72f * progress.value }
                    .background(Color.Black)
                    .clickable(
                        enabled = mounted,
                        interactionSource = backdropInteraction,
                        indication = null,
                        onClick = {},
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .align(Alignment.BottomCenter)
                    .clickable(
                        enabled = mounted,
                        interactionSource = sheetInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .graphicsLayer {
                        val hiddenDistance = size.height + 40.dp.toPx()
                        translationY = (1f - progress.value) * hiddenDistance + dragOffset + dragAnimation.value
                    }
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(ComposeModalSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                    ComposePostHeader(
                        profile = profile,
                        authUuid = profile?.uuid,
                        title = title,
                        onTitleChange = { title = it },
                        dismissConfirmationVisible = dismissConfirmationVisible,
                        onRequestClose = {
                            optionsOpen = false
                            topicOpen = false
                            if (hasDraftContent) {
                                dismissConfirmationVisible = true
                            } else {
                                closeModal()
                            }
                        },
                        onConfirmClose = ::closeModal,
                        onDismissControlBoundsChanged = { dismissControlBounds = it },
                        dragHandleModifier = Modifier.appBottomSheetDragHandle(
                            onDragStart = { dragOffset = 0f },
                            onDrag = { dragAmount -> dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f) },
                            onDragEnd = {
                                if (dragOffset > dragCloseThreshold) {
                                    optionsOpen = false
                                    topicOpen = false
                                    if (hasDraftContent) dismissConfirmationVisible = true else closeModal()
                                }
                                settleDragBack()
                            },
                            onDragCancel = ::settleDragBack,
                        ),
                    )

                    ComposePostEditorContent(
                        body = body,
                        onBodyChange = ::updateBody,
                        styleRanges = styleRanges,
                        bodyScrollState = bodyScrollState,
                        bringIntoViewRequester = bodyBringIntoViewRequester,
                        focusRequester = bodyFocusRequester,
                        onFocusChanged = { bodyFocused = it },
                        bodyTextLayout = bodyTextLayout,
                        onTextLayout = { bodyTextLayout = it },
                        mentionAliases = mentionAliases,
                        mentionApi = mentionApi,
                        mentionAuth = mentionAuth,
                        mediaUris = mediaUris,
                        onRemoveMedia = { index -> mediaUris = mediaUris.filterIndexed { i, _ -> i != index } },
                        quotedPost = draftQuotedPost,
                        activeOption = activeOption,
                        pollOptions = pollOptions,
                        onPollOptionsChange = { pollOptions = it },
                        pollLink = pollLink,
                        onPollLinkChange = { value -> pollLink = value; if (value.isNotBlank()) mediaUris = emptyList() },
                        onRemoveOption = { activeOption = null },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    ComposePostToolbar(
                        optionsOpen = optionsOpen || activeOption != null || mediaUris.isNotEmpty(),
                        topicOpen = topicOpen,
                        topic = topic,
                        hasSelection = hasSelection,
                        canPost = canPost,
                        isSubmitting = isSubmitting,
                        draftsCount = draftsCount,
                        savedFeedback = savedFeedback,
                        onToggleOptions = {
                            topicOpen = false
                            optionsOpen = !optionsOpen
                        },
                        onToggleTopic = {
                            optionsOpen = false
                            topicOpen = !topicOpen
                        },
                        onObfuscate = { updateBody(body.obfuscateComposeSelection()) },
                        onSaveDraft = ::saveCurrentDraft,
                        onOpenDrafts = {
                            keyboardController?.hide()
                            draftsOpen = true
                        },
                        onPost = ::submitCurrentPost,
                        bottomPadding = toolbarBottomPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding(),
                    )

                    }

                    ComposePostFormatBar(
                        bold = boldActive,
                        italic = italicActive,
                        bullets = bulletsActive,
                        quote = quoteActive,
                        onBold = { toggleMark(ComposeMark.Bold) },
                        onItalic = { toggleMark(ComposeMark.Italic) },
                        onBullets = {
                            val edit = body.toggleComposeLinePrefix("• ")
                            bulletsActive = edit.active
                            updateBody(edit.value)
                        },
                        onQuote = {
                            val edit = body.toggleComposeLinePrefix("│ ")
                            quoteActive = edit.active
                            updateBody(edit.value)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (optionsOpen || topicOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    optionsOpen = false
                                    topicOpen = false
                                },
                        )
                    }

                    ComposePostFormatMenus(
                        optionsOpen = optionsOpen,
                        topicOpen = topicOpen,
                        activeOption = activeOption,
                        topic = topic,
                        pinnedTopic = pinnedTopic,
                        mediaCount = mediaUris.size,
                        hasVideo = hasVideoSelection,
                        onImages = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        onPoll = {
                            val nextOption = if (activeOption == ComposePostOption.Poll) null else ComposePostOption.Poll
                            if (nextOption == null) { mediaUris = emptyList(); pollLink = "" }
                            activeOption = nextOption
                            optionsOpen = false
                        },
                        onLikert = {
                            val nextOption = if (activeOption == ComposePostOption.Likert) null else ComposePostOption.Likert
                            if (nextOption != null) mediaUris = emptyList()
                            activeOption = nextOption
                            optionsOpen = false
                        },
                        onGif = {
                            optionsOpen = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            gifPickerOpen = true
                        },
                        onTopic = { topic = it; topicOpen = false },
                        onPin = { pinnedTopic = it },
                        menuBottomPadding = menuBottomPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                    )
                }
            }
        }
    }
    ComposePostAuxiliarySheets(
        gifPickerOpen = gifPickerOpen,
        draftsOpen = draftsOpen,
        drafts = storedDrafts,
        onDismissGifPicker = {
            gifPickerOpen = false
            bodyFocusRequester.requestFocus()
            keyboardController?.show()
        },
        onSelectGif = { url ->
            mediaUris = listOf(url)
            gifPickerOpen = false
            bodyFocusRequester.requestFocus()
            keyboardController?.show()
        },
        onDismissDrafts = { draftsOpen = false },
        onLoadDraft = { stored ->
            val draft = stored.draft
            title = draft.title
            updateBody(TextFieldValue(draft.body.take(5_000)))
            styleRanges = emptyList()
            topic = draft.topic
            activeOption = draft.option
            pollOptions = draft.pollOptions.ifEmpty { listOf("", "") }
            pollLink = draft.pollLink.orEmpty()
            mediaUris = draft.mediaUris
            draftQuotedPost = draft.quotedPost
            loadedDraftId = stored.id
            draftsOpen = false
            scope.launch {
                withFrameNanos { }
                bodyFocusRequester.requestFocus()
                keyboardController?.show()
            }
        },
        onDeleteDraft = { stored ->
            scope.launch {
                storedDrafts = draftStore.delete(stored.id)
                draftsCount = storedDrafts.size
                if (loadedDraftId == stored.id) loadedDraftId = null
            }
        },
    )
}
