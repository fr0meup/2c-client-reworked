package com.twocents.mobile.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCommentThreadTest {
    private fun comment(id: String, parent: String? = null) = PostComment(
        uuid = id,
        createdAt = id,
        postUuid = "post",
        parentUuid = parent,
        authorUuid = "author",
        author = FeedAuthor(),
        text = id,
        upvoteCount = 0,
        deleted = false,
        mediaUrls = emptyList(),
    )

    @Test fun deepThreadsRetainActualParentsWithoutRecursion() {
        val comments = (0..1_000).map { index ->
            comment("comment-$index", if (index == 0) null else "comment-${index - 1}")
        }
        val rows = flattenPostComments(comments, CommentSort.Oldest)

        assertEquals(comments.size, rows.size)
        assertEquals(1_000, rows.last().depth)
        assertEquals("comment-999", rows.last().parent?.uuid)
        assertTrue(rows.all { it.rootUuid == "comment-0" })
        assertNull(rows.first().parent)
    }

    @Test fun siblingOrderAndParentReferencesRemainIntact() {
        val rows = flattenPostComments(
            listOf(comment("root"), comment("a", "root"), comment("b", "root"), comment("child", "a")),
            CommentSort.Oldest,
        )

        assertEquals(listOf("root", "a", "child", "b"), rows.map { it.comment.uuid })
        assertEquals(listOf(0, 1, 2, 1), rows.map { it.depth })
        assertEquals("a", rows[2].parent?.uuid)
        assertTrue(rows.all { it.rootUuid == "root" })
    }

    @Test fun pagesShowFiveLevelsAndOnlyContinueTheSelectedBranch() {
        val chain = (0..12).map { comment("n$it", if (it == 0) null else "n${it - 1}") }
        val index = CommentThreadIndex(flattenPostComments(chain + comment("other"), CommentSort.Oldest))
        val main = index.page()
        assertEquals(listOf("n0", "n1", "n2", "n3", "n4", "other"), main.rows.map { it.comment.uuid })
        assertEquals(8, main.moreReplies["n4"])
        val deeper = index.page("n4")
        assertEquals(listOf("n4", "n5", "n6", "n7", "n8"), deeper.rows.map { it.comment.uuid })
        assertEquals(listOf(0, 1, 2, 3, 4), deeper.rows.map { it.depth })
        assertEquals(listOf("n3"), deeper.ancestors.map { it.uuid })
        assertEquals(listOf("n4", "n8"), index.pathTo("n12"))
        assertTrue(index.pathTo("n4").isEmpty())
    }
}
