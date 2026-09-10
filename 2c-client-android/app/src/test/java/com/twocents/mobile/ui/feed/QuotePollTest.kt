package com.twocents.mobile.ui.feed

import org.junit.Assert.*
import org.junit.Test

class QuotePollTest {
    private fun post(type: Int, options: List<String>) = FeedPost(
        "quote", "", "author", 0, 0, 0, "", "", "lounge", FeedAuthor(),
        FeedPostMeta(poll = options), type,
    )
    @Test fun attachedPollDoesNotDependOnPostType() {
        assertTrue(post(3, listOf("Yes", "No")).hasPoll)
        assertTrue(post(2, listOf("Yes", "No")).hasPoll)
        assertFalse(post(3, emptyList()).hasPoll)
        assertEquals("poll", post(3, listOf("Yes", "No")).recycleType)
    }
}
