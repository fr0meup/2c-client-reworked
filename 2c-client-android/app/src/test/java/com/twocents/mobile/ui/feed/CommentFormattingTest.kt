package com.twocents.mobile.ui.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentFormattingTest {
    @Test fun authoredMutationsUseMarkdownRenderer() {
        assertTrue(commentUsesPostFormatting("hello **bold** and *italic*"))
        assertTrue(commentUsesPostFormatting("## heading\nbody"))
        assertTrue(commentUsesPostFormatting("> quoted"))
    }

    @Test fun plainCommentsKeepExistingRenderer() {
        assertFalse(commentUsesPostFormatting("Plain comment with \$16 and two lines\nnext line"))
    }
}
