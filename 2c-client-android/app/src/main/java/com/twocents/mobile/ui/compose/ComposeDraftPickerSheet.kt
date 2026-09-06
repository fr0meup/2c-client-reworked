package com.twocents.mobile.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeDraftPickerSheet(
    drafts: List<StoredComposeDraft>,
    onDismiss: () -> Unit,
    onLoad: (StoredComposeDraft) -> Unit,
    onDelete: (StoredComposeDraft) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF141410),
        contentColor = Color.White,
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 5.dp).size(width = 42.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))) },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.72f).navigationBarsPadding().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Drafts", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${drafts.size} saved", color = Color.White.copy(alpha = .38f), fontSize = 11.sp)
                Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(7.dp))
            }
            if (drafts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No saved drafts", color = Color.White.copy(alpha = .38f), fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 18.dp),
                ) {
                    items(drafts, key = StoredComposeDraft::id) { stored ->
                        val body = stored.draft.body.replace("\u200B", "").trim()
                        val mediaCount = stored.draft.mediaUris.size
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFC8A44D).copy(alpha = .055f), Color.White.copy(alpha = .022f))))
                                .border(.7.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(16.dp))
                                .clickable { onLoad(stored) }
                        ) {
                          Box(Modifier.fillMaxHeight().width(2.dp).background(Color(0xFFC8A44D).copy(alpha = .5f)).align(Alignment.CenterStart))
                          Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 9.dp, bottom = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (stored.draft.option) { ComposePostOption.Poll -> Icons.Outlined.Poll; ComposePostOption.Likert -> Icons.Outlined.Description; else -> if (mediaCount > 0) Icons.Outlined.Image else Icons.Outlined.Description },
                                    null, tint = Color(0xFFC8A44D).copy(alpha = .65f), modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    stored.draft.title.ifBlank { body.lineSequence().firstOrNull().orEmpty().ifBlank { "Untitled draft" } },
                                    color = Color.White.copy(alpha = .9f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 7.dp).weight(1f),
                                )
                            }
                            if (body.isNotBlank() && body != stored.draft.title) Text(body, color = Color.White.copy(alpha = .42f), fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$/" + stored.draft.topic.lowercase(), color = Color(0xFFC8A44D).copy(alpha = .68f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                val details = buildList {
                                    add(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(stored.savedAt)))
                                    if (mediaCount > 0) add(if (mediaCount == 1) "1 attachment" else "$mediaCount attachments")
                                    when (stored.draft.option) {
                                        ComposePostOption.Poll -> add("poll")
                                        ComposePostOption.Likert -> add("likert")
                                        else -> Unit // Image/GIF attachments are already represented by mediaCount.
                                    }
                                }.joinToString("  ·  ")
                                Text(details, color = Color.White.copy(alpha = .31f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Box(Modifier.size(32.dp).clip(CircleShape).clickable { onDelete(stored) }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.DeleteOutline, "Delete draft", tint = Color(0xFFF43F5E).copy(alpha = .62f), modifier = Modifier.size(15.dp)) }
                            }
                          }
                        }
                    }
                }
            }
        }
    }
}
