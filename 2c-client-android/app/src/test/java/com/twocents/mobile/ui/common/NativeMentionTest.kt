package com.twocents.mobile.ui.common

import org.junit.Assert.*
import org.junit.Test

class NativeMentionTest {
    private val uuid = "01234567-89ab-cdef-0123-456789abcdef"
    @Test fun finalMentionHasExactlyOneDelimiter() {
        assertEquals("@$uuid ", terminateMentionToken("@$uuid"))
        assertEquals("@$uuid ", terminateMentionToken("@$uuid "))
        assertEquals("@$uuid hello", terminateMentionToken("@$uuid hello"))
        assertEquals("", terminateMentionToken(""))
    }
    @Test fun renderingTwiceDoesNotNestLinks() {
        val once = renderOfficialMentions("@$uuid", null)
        assertEquals(once, renderOfficialMentions(once, null))
    }
    @Test fun nativeTokenBecomesClickableEvenWithoutMetadata() {
        assertEquals("Hi [@$uuid](/user/$uuid)!", renderOfficialMentions("Hi @$uuid!", null))
    }
    @Test fun oldMentionsRemainIntact() {
        val old = "[@16](/user/$uuid)"
        assertEquals(old, renderOfficialMentions(old, null))
    }
    @Test fun caseSensitiveUuidIsPreserved() {
        val upper = uuid.uppercase()
        assertEquals(upper, NativeMention.find("@$upper")!!.groupValues[1])
    }
    @Test fun uuidInsideUrlIsNotAMention() {
        assertNull(NativeMention.find("https://example.com/@$uuid"))
    }
}
