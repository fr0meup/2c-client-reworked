package com.twocents.mobile.ui.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.common.MentionSuggestions
import com.twocents.mobile.ui.common.mentionContext
import com.twocents.mobile.ui.common.mentionMarkup
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.FeedQuoteCard

/** Stateless editor body; the modal remains the sole owner of draft mutations. */
@Composable
internal fun ComposePostEditorContent(
    body: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    styleRanges: List<ComposeStyleRange>,
    bodyScrollState: ScrollState,
    bringIntoViewRequester: BringIntoViewRequester,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    bodyTextLayout: TextLayoutResult?,
    onTextLayout: (TextLayoutResult) -> Unit,
    mentionAliases: Map<String, String>,
    mentionApi: RpcApi?,
    mentionAuth: AuthState?,
    mediaUris: List<String>,
    onRemoveMedia: (Int) -> Unit,
    quotedPost: FeedPost?,
    activeOption: ComposePostOption?,
    pollOptions: List<String>,
    onPollOptionsChange: (List<String>) -> Unit,
    pollLink: String,
    onPollLinkChange: (String) -> Unit,
    onRemoveOption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var editorFocused by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(bodyScrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).zIndex(20f)) {
                BasicTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(top = 2.dp)
                        .bringIntoViewRequester(bringIntoViewRequester).focusRequester(focusRequester)
                        .onFocusChanged {
                            editorFocused = it.isFocused
                            onFocusChanged(it.isFocused)
                        },
                    textStyle = TextStyle(
                        color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, lineHeight = 22.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    cursorBrush = SolidColor(Color.White),
                    visualTransformation = ComposeRichTextTransformation(styleRanges),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    onTextLayout = onTextLayout,
                    decorationBox = { innerTextField ->
                        Box {
                            if (body.text.isEmpty()) {
                                androidx.compose.material3.Text(
                                    text = "Share your twocents...", color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 16.sp, lineHeight = 22.sp,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                // TextFieldValue can update one frame before TextLayoutResult. Clamp
                // against the layout snapshot as well as the current editor value.
                val cursorRect = bodyTextLayout?.let { layout ->
                    layout.getCursorRect(body.selection.end.coerceIn(0, layout.layoutInput.text.text.length))
                }
                val cursorTopDp = with(density) { (cursorRect?.top ?: 0f).toDp() }
                val suggestionsAbove = cursorTopDp > 190.dp
                MentionSuggestions(
                    context = if (editorFocused) mentionContext(body.text, body.selection.end) else null,
                    aliases = mentionAliases,
                    onShown = {},
                    onDismiss = {},
                    onSelect = { uuid, alias ->
                        val mention = mentionContext(body.text, body.selection.end) ?: return@MentionSuggestions
                        val markup = mentionMarkup(alias, uuid)
                        val next = body.text.replaceRange(mention.start, body.selection.end, markup)
                        onBodyChange(TextFieldValue(next, TextRange(mention.start + markup.length)))
                        focusRequester.requestFocus()
                    },
                    offset = DpOffset(
                        x = 0.dp,
                        y = if (suggestionsAbove) cursorTopDp else with(density) { (cursorRect?.bottom ?: 20f).toDp() },
                    ),
                    placeAbove = suggestionsAbove,
                    gap = 7.dp,
                    modifier = Modifier.widthIn(max = 328.dp),
                    api = mentionApi,
                    auth = mentionAuth,
                )
            }
            ComposeMediaRow(mediaUris = mediaUris, onRemove = onRemoveMedia)
            quotedPost?.let { FeedQuoteCard(it) }
            if (activeOption == ComposePostOption.Poll) {
                ComposePollCard(
                    options = pollOptions, onChange = onPollOptionsChange, link = pollLink,
                    onLinkChange = onPollLinkChange, onRemove = onRemoveOption,
                )
            }
            if (activeOption == ComposePostOption.Likert) ComposeLikertCard(onRemove = onRemoveOption)
            Box(modifier = Modifier.fillMaxWidth().height(48.dp))
        }
    }
}
