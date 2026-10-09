package com.twocents.mobile.ui.common

import com.twocents.mobile.ui.compose.ComposePostDraft
import com.twocents.mobile.ui.compose.ComposePostOption
import com.twocents.mobile.ui.compose.validateComposeChart
import com.twocents.mobile.ui.feed.FeedAuthor
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.FeedPostMeta
import org.junit.Assert.*
import org.junit.Test

class EquityChartAttachmentTest {
    private val chart = EquityChartAttachment("AAPL", "Apple Inc.", "XNAS", "USD", 332.945)
    private fun draft(option: ComposePostOption? = ComposePostOption.EquityChart, attachment: EquityChartAttachment? = chart) =
        ComposePostDraft("", "testis", "Lounge", option, emptyList(), emptyList(), equityChart = attachment)

    @Test fun attachmentDoesNotRequireDollarText() {
        assertEquals(listOf(PostTickerReference("AAPL", true)), postTickerReferences("testis", chart))
        validateComposeChart(draft())
    }

    @Test fun ordinaryTickersStayInline() {
        assertEquals(listOf(PostTickerReference("MSFT", false)), postTickerReferences("Buy \$MSFT", null))
        assertTrue(postTickerReferences("\$16 https://example.com/\$AAPL", null).isEmpty())
    }

    @Test fun sameTickerIsNotAttachedTwice() {
        assertEquals(listOf(PostTickerReference("AAPL", true), PostTickerReference("MSFT", false)),
            postTickerReferences("\$aapl \$AAPL \$MSFT \$msft", chart))
    }

    @Test fun chartContentIsRecognizedInNormalAndQuotedPosts() {
        fun post(type: Int, meta: FeedPostMeta) = FeedPost("post", "", "author", 0, 0, 0, "", "", "lounge", FeedAuthor(), meta, type)
        assertEquals("equity-chart", post(6, FeedPostMeta(equityCharts = listOf(chart))).recycleType)
        assertEquals("equity-chart", post(3, FeedPostMeta(equityCharts = listOf(chart))).recycleType)
        assertEquals("text", post(0, FeedPostMeta()).recycleType)
    }

    @Test fun chartMetadataSurvivesDraftCopyAndOrdinaryDraftsStayCompatible() {
        assertEquals(chart, draft().copy(body = "changed").equityChart)
        assertNull(draft(null, null).equityChart)
        validateComposeChart(draft(null, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingTickerCannotBePublished() { validateComposeChart(draft(attachment = null)) }

    @Test(expected = IllegalArgumentException::class)
    fun chartCannotSilentlyReplaceImageUpload() { validateComposeChart(draft().copy(mediaUris = listOf("content://image"))) }

    @Test(expected = IllegalArgumentException::class)
    fun pollCannotPublishAsChart() { validateComposeChart(draft(option = ComposePostOption.Poll)) }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTickerIsRejectedBeforeAnyUpload() { validateComposeChart(draft(attachment = chart.copy(symbol = "AAPL/invalid"))) }

    @Test fun validSymbolsIncludeClassAndExchangeVariants() {
        assertTrue(validEquitySymbol("BRK.B"))
        assertTrue(validEquitySymbol("ABC-DEF"))
        assertFalse(validEquitySymbol("\$AAPL"))
        assertFalse(validEquitySymbol(""))
    }
}
