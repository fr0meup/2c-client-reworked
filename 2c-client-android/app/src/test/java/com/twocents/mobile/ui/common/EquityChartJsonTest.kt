package com.twocents.mobile.ui.common

import com.twocents.mobile.ui.compose.ComposePostDraft
import com.twocents.mobile.ui.compose.ComposePostOption
import com.twocents.mobile.ui.compose.toComposeDraft
import com.twocents.mobile.ui.compose.toDraftJson
import com.twocents.mobile.ui.feed.parseFeedPost
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Exercise real org.json data, not manually constructed FeedPost models. */
class EquityChartJsonTest {
    private fun contractPost(): JSONObject = JSONObject("""
        {"uuid":"chart-post","created_at":"2026-10-09T14:01:44.982Z",
         "author_uuid":"author","title":"","text":"testis","topic":"lounge",
         "author_meta":{"balance":16.34,"subscription_type":1},
         "post_meta":{"tickers":[{"name":"Apple Inc.","price":332.945,
             "ticker":"AAPL","currency":"USD","exchange":"XNAS","is_attachment":true}],
             "version":1,"mentions":[],"platform":"web"},
         "post_type":6,"deleted_at":null,"edited_at":null,"isEditable":true}
    """.trimIndent())

    @Test fun suppliedGetResponseRetainsAttachmentThroughFeedParser() {
        val response = JSONObject().put("post", contractPost()).put("votes", JSONArray())
        val post = requireNotNull(parseFeedPost(response.getJSONObject("post")))
        assertEquals(6, post.postType)
        assertEquals("equity-chart", post.recycleType)
        assertEquals(listOf(EquityChartAttachment("AAPL", "Apple Inc.", "XNAS", "USD", 332.945)), post.meta.equityCharts)
    }

    @Test fun chartPostsRetainTickerWhenFeedProjectionOmitsFlag() {
        val raw = contractPost()
        raw.getJSONObject("post_meta").getJSONArray("tickers").getJSONObject(0).remove("is_attachment")
        assertEquals("AAPL", requireNotNull(parseFeedPost(raw)).meta.equityCharts.single().symbol)
        raw.put("post_type", 0)
        assertTrue(requireNotNull(parseFeedPost(raw)).meta.equityCharts.isEmpty())
    }

    @Test fun explicitNonAttachmentNeverBecomesChart() {
        val raw = contractPost()
        raw.getJSONObject("post_meta").getJSONArray("tickers").getJSONObject(0).put("is_attachment", false)
        assertTrue(requireNotNull(parseFeedPost(raw)).meta.equityCharts.isEmpty())
    }

    @Test fun serializedTickerArrayAndSerializedPostMetaAreAccepted() {
        val raw = contractPost()
        val meta = raw.getJSONObject("post_meta")
        meta.put("tickers", meta.getJSONArray("tickers").toString())
        raw.put("post_meta", meta.toString())
        assertEquals("AAPL", requireNotNull(parseFeedPost(raw)).meta.equityCharts.single().symbol)
    }

    @Test fun quoteParsingKeepsOriginalChart() {
        val outer = JSONObject().put("uuid", "quote").put("post_type", 3)
            .put("post_meta", JSONObject().put("quote_post", contractPost()))
        assertEquals("AAPL", requireNotNull(parseFeedPost(outer)).meta.quotePost?.meta?.equityCharts?.single()?.symbol)
    }

    @Test fun requestMatchesOfficialAttachmentContractAndKeepsInlineTickersDistinct() {
        val values = composeTickerMetadata("testis", EquityChartAttachment("AAPL"))
        assertEquals(1, values.length())
        assertEquals(setOf("ticker", "is_attachment"), values.getJSONObject(0).keys().asSequence().toSet())
        assertEquals("AAPL", values.getJSONObject(0).getString("ticker"))
        assertTrue(values.getJSONObject(0).getBoolean("is_attachment"))
        val mixed = composeTickerMetadata("\$AAPL \$MSFT", EquityChartAttachment("AAPL"))
        assertEquals(2, mixed.length())
        assertFalse(mixed.getJSONObject(1).optBoolean("is_attachment"))
        assertEquals("MSFT", mixed.getJSONObject(1).getString("ticker"))
        assertEquals("MSFT", composeTickerMetadata("\$MSFT", null).getString(0))
    }

    @Test fun chartAndQuotedChartSurviveRealDraftJsonRoundTrip() {
        val chart = requireNotNull(parseFeedPost(contractPost()))
        val draft = ComposePostDraft("", "draft", "Lounge", ComposePostOption.EquityChart, listOf("", ""), emptyList(),
            equityChart = chart.meta.equityCharts.single())
        assertEquals(draft, JSONObject(draft.toDraftJson().toString()).toComposeDraft())
        val quote = draft.copy(option = null, equityChart = null, quotedPost = chart)
        val restored = JSONObject(quote.toDraftJson().toString()).toComposeDraft()
        assertEquals(chart.meta.equityCharts, restored.quotedPost?.meta?.equityCharts)
    }
}
