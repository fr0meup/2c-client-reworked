package com.twocents.mobile.ui.feed

import org.junit.Assert.*
import org.junit.Test

class VerifiedContentFilterTest {
    private fun post(author: String = "unverified", role: String? = null, type: Int = 0, subscription: Int = 0) =
        FeedPost("post", "", author, 0, 0, 0, "", "", "lounge",
            FeedAuthor(subscriptionType = subscription, role = role), FeedPostMeta(), type)

    @Test fun newsAndPicksStayVisibleWhileOrdinaryUnverifiedContentIsHidden() {
        assertTrue(VerifiedContentFilterStore.allows(post(author = "news")))
        assertTrue(VerifiedContentFilterStore.allows(post(role = "news")))
        assertTrue(VerifiedContentFilterStore.allows(post(type = 7)))
        assertTrue(VerifiedContentFilterStore.allows(post(subscription = 1)))
        assertFalse(VerifiedContentFilterStore.allows(post()))
        assertFalse(VerifiedContentFilterStore.allows(post(type = 2)))
    }
}
