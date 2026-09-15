package com.twocents.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MentionRequestTest {
    private val first = "01234567-89ab-cdef-0123-456789abcdef"
    private val second = "ABCDEF01-2345-6789-abcd-0123456789ab"
    @Test fun sendsAliasNotUuidOrNetworth() {
        assertEquals("@test account ", mentionRequestText("@$first ", mapOf(first to "test account")))
    }
    @Test fun multipleMentionsPreserveWhitespaceAndCaseSensitiveIdentity() {
        assertEquals("hello @daddy dan\n\n@test account ", mentionRequestText("hello @$second\n\n@$first ",
            mapOf(first to "test account", second to "daddy dan")))
    }
}
