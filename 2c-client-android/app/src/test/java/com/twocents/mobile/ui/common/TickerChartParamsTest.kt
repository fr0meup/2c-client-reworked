package com.twocents.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TickerChartParamsTest {
    private val now = 1_790_184_000_000L

    @Test fun shortPeriodsUseTheBackendAggregationCadence() {
        val day = tickerChartSpec(TickerPeriod.Day, now)
        assertEquals(5, day.multiplier)
        assertEquals("minute", day.timespan)
        assertEquals((now - 5L * 86_400_000).toString(), day.from)
        val week = tickerChartSpec(TickerPeriod.Week, now)
        assertEquals(30, week.multiplier)
        assertEquals("minute", week.timespan)
        val month = tickerChartSpec(TickerPeriod.Month, now)
        assertEquals(1, month.multiplier)
        assertEquals("hour", month.timespan)
    }

    @Test fun longPeriodsUseUtcDatesAndBoundedAscendingBars() {
        val year = tickerChartSpec(TickerPeriod.Year, now)
        assertEquals("day", year.timespan)
        assertEquals(1, year.multiplier)
        val all = tickerChartSpec(TickerPeriod.All, now)
        assertEquals("week", all.timespan)
        assertEquals("1970-01-01", all.from)
    }

    @Test fun dayGraphKeepsTheLatestAvailableSession() {
        val bars = listOf(TickerPoint(86_400_000, 12.0), TickerPoint(2 * 86_400_000, 13.0),
            TickerPoint(2 * 86_400_000 + 60_000, 14.0))
        assertEquals(bars.drop(1), latestTradingDay(bars))
    }

    @Test fun largeGraphRetainsSpikesWhileBoundingDrawWork() {
        val bars = (0 until 10_000).map { index -> TickerPoint(index.toLong(),
            when (index) { 501 -> 20.0; 502 -> -10.0; else -> 1.0 }) }
        val indices = chartDrawIndices(bars, maxBuckets = 100)
        assertEquals(0, indices.first())
        assertEquals(bars.lastIndex, indices.last())
        assert(indices.size <= 202)
        assert(indices.contains(501) && indices.contains(502))
    }

    @Test fun flatChartStillHasReadablePriceAxis() {
        val bounds = tickerChartBounds(listOf(TickerPoint(0, 14.68), TickerPoint(1, 14.68)))!!
        assert(bounds.low < 14.68)
        assert(bounds.high > 14.68)
    }
}
