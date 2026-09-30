package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.toUserDisplay

/** Show the immediate parent as a local comment embed, using the loaded thread snapshot. */
@Composable
internal fun PostCommentThreadContext(comment: PostComment, alias: String?) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape).background(Color.White.copy(alpha = .02f))
            .border(1.dp, Color.White.copy(alpha = .08f), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Parent comment", color = Color(0xFFC8A44D), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UserNetworthPill(comment.author.toUserDisplay(comment.authorUuid, alias), compact = true)
            CommentTimestamp(comment.createdAt, Modifier.weight(1f))
        }
        if (comment.deleted) {
            Text("[deleted]", color = Color.White.copy(alpha = .4f), fontSize = 13.sp)
        } else {
            ExpandableCommentBody(comment.visibleText(), "thread-parent-${comment.uuid}")
            CommentImageAttachments(comment.mediaUrls)
            commentTweetUrl(comment.text)?.let { TweetEmbedCard(it) }
        }
        UserMetaPill(comment.author.toUserDisplay(comment.authorUuid, alias), alias, Modifier.fillMaxWidth(), compact = true)
    }
}
