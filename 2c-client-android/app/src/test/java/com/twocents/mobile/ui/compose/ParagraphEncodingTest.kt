package com.twocents.mobile.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphEncodingTest {
    @Test fun whitespaceTrimmingMatchesVersion024() {
        val mention = "@01234567-89ab-cdef-0123-456789abcdef"
        assertEquals(mention, formatComposeTextForApi("$mention "))
    }
    @Test fun paragraphUsesOriginalFillerSpacing() {
        assertEquals("first\n\n\u3164\n\nsecond", formatComposeTextForApi("first\n\nsecond"))
    }
    @Test fun repeatedParagraphGapsCollapse() {
        assertEquals("first\n\n\u3164\n\nsecond", formatComposeTextForApi("first\n\n\n\nsecond"))
    }
    @Test fun ordinaryEnterIsUnchanged() {
        assertEquals("first\n\nsecond", formatComposeTextForApi("first\nsecond"))
    }
    @Test fun mixedBreaksPreserveIntent() {
        assertEquals("a\n\nb\n\n\u3164\n\nc", formatComposeTextForApi("a\nb\n\nc"))
    }
}
