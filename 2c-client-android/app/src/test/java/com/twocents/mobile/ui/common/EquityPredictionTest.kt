package com.twocents.mobile.ui.common

import com.twocents.mobile.ui.compose.*
import com.twocents.mobile.ui.feed.parseFeedPost
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EquityPredictionTest {
    private val today = LocalDate.of(2026, 10, 9)
    private val prediction = EquityPrediction("AAPL", 316.01, "2026-10-16", "Apple Inc.", 332.6441, "below", "open")
    private fun draft() = ComposePostDraft("", "example", "Lounge", ComposePostOption.EquityPrediction,
        listOf("", ""), emptyList(), equityPrediction = prediction)

    @Test fun payloadContainsOnlyTheThreeOfficialRequestFields() {
        val wire = prediction.toPredictionRequest()
        assertEquals(setOf("ticker", "targetPrice", "targetDate"), wire.keys().asSequence().toSet())
        assertEquals("AAPL", wire.getString("ticker"))
        assertEquals(316.01, wire.getDouble("targetPrice"), 0.0)
        assertEquals("2026-10-16", wire.getString("targetDate"))
        assertEquals(11, composePostType(draft()))
        validateComposePrediction(draft(), today)
    }

    @Test fun suppliedPostResponseParsesServerBaselineDirectionAndStatus() {
        val raw = JSONObject("""{"uuid":"prediction-post","post_type":11,"text":"example",
            "post_meta":{"stockPrediction":{"name":"Apple Inc.","ticker":"AAPL","direction":"below",
            "targetDate":"2026-10-16","targetPrice":316.01,"currentPrice":332.6441,"resolutionStatus":"open"}}} """)
        val post = requireNotNull(parseFeedPost(raw))
        assertEquals(prediction, post.meta.equityPrediction)
        assertEquals("equity-prediction", post.recycleType)
        assertEquals(-5.0, requireNotNull(predictionPercent(prediction)), .01)
    }

    @Test fun quoteAndDraftRoundTripsPreservePredictionMetadata() {
        val original = JSONObject().put("uuid", "original").put("post_type", 11)
            .put("post_meta", JSONObject().put("stockPrediction", prediction.toPredictionJson()))
        val outer = JSONObject().put("uuid", "quote").put("post_type", 3)
            .put("post_meta", JSONObject().put("quote_post", original))
        assertEquals(prediction, parseFeedPost(outer)?.meta?.quotePost?.meta?.equityPrediction)
        assertEquals(draft(), JSONObject(draft().toDraftJson().toString()).toComposeDraft())
        val quoteDraft = draft().copy(option = null, equityPrediction = null, quotedPost = parseFeedPost(original))
        assertEquals(prediction, JSONObject(quoteDraft.toDraftJson().toString()).toComposeDraft().quotedPost?.meta?.equityPrediction)
    }

    @Test fun presetsMatchTheOfficialExampleAndDoNotAccumulateRounding() {
        assertEquals(316.01, predictionPresetPrice(332.6441, -5), 0.0)
        assertEquals(399.17, predictionPresetPrice(332.6441, 20), 0.0)
        assertEquals(349.28, predictionPresetPrice(332.6441, 5), 0.0)
    }

    @Test fun datePresetsSkipWeekendsAndUseCalendarMonths() {
        assertEquals(LocalDate.of(2026, 10, 12), predictionPresetDate("1D", today))
        assertEquals(LocalDate.of(2026, 10, 16), predictionPresetDate("1W", today))
        assertEquals(LocalDate.of(2026, 10, 23), predictionPresetDate("2W", today))
        assertEquals(LocalDate.of(2026, 11, 9), predictionPresetDate("1M", today))
        assertFalse(validPredictionDate(today, today))
        assertFalse(validPredictionDate(today.plusDays(1), today))
        assertTrue(validPredictionDate(today.plusDays(3), today))
    }

    @Test fun elapsedDateDoesNotInventResolutionOutcome() {
        assertEquals("7 days left", predictionStatusLabel(prediction, today))
        assertEquals("Resolves today", predictionStatusLabel(prediction, LocalDate.of(2026, 10, 16)))
        assertEquals("Awaiting resolution", predictionStatusLabel(prediction, LocalDate.of(2026, 10, 17)))
        assertEquals("Resolved", predictionStatusLabel(prediction.copy(resolutionStatus = "resolved"), today))
        assertEquals("Cancelled", predictionStatusLabel(prediction.copy(resolutionStatus = "cancelled"), today))
    }

    @Test fun priceEntryAcceptsDecimalLocalesAndRejectsInvalidNumbers() {
        assertEquals(316.01, requireNotNull(parsePredictionPrice("316,01")), 0.0)
        assertEquals(316.01, requireNotNull(parsePredictionPrice("316.01")), 0.0)
        listOf("", "0", "-1", "NaN", "Infinity", "1.2.3", "1,000.00").forEach { assertNull(parsePredictionPrice(it)) }
    }

    @Test fun malformedServerPredictionsCannotProduceBrokenCards() {
        assertNull(parseEquityPrediction(JSONObject().put("ticker", "AAPL").put("targetPrice", 0).put("targetDate", "2026-10-16")))
        assertNull(parseEquityPrediction(JSONObject().put("ticker", "AAPL").put("targetPrice", 1).put("targetDate", "not-a-date")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun expiredDraftMustChooseANewDate() { validateComposePrediction(draft(), LocalDate.of(2026, 10, 16)) }

    @Test(expected = IllegalArgumentException::class)
    fun predictionCannotReplaceMediaSilently() { validateComposePrediction(draft().copy(mediaUris = listOf("content://image")), today) }

    @Test(expected = IllegalArgumentException::class)
    fun predictionCannotBeCombinedWithChart() { validateComposePrediction(draft().copy(equityChart = EquityChartAttachment("AAPL")), today) }

    @Test fun existingPostTypesRemainUnchanged() {
        val ordinary = draft().copy(option = null, equityPrediction = null)
        assertEquals(0, composePostType(ordinary))
        assertEquals(2, composePostType(ordinary.copy(option = ComposePostOption.Poll)))
        assertEquals(5, composePostType(ordinary.copy(option = ComposePostOption.Likert)))
        assertEquals(4, composePostType(ordinary, hasImages = true))
        assertEquals(10, composePostType(ordinary, hasVideo = true))
        assertEquals(6, composePostType(ordinary.copy(option = ComposePostOption.EquityChart, equityChart = EquityChartAttachment("AAPL"))))
    }
}
