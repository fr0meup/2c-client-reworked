package com.twocents.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TickerChartParamsTest {
    @Test fun intervalMetricsFollowTheSelectedChart() {
        val bars = listOf(TickerPoint(1, 10.0, 100.0), TickerPoint(2, 11.0, 120.0), TickerPoint(3, 12.0, 130.0))
        val daily = TickerPrice(12.0, 2.0, 20.0, 350.0)
        val week = tickerPeriodStats(TickerPeriod.Week, bars, daily)
        org.junit.Assert.assertEquals(2.0, week.change!!, 0.0001)
        org.junit.Assert.assertEquals(20.0, week.percent!!, 0.0001)
        org.junit.Assert.assertEquals(350.0, week.volume!!, 0.0001)
        val selected = tickerPeriodStats(TickerPeriod.Week, bars, daily, 1)
        org.junit.Assert.assertEquals(1.0, selected.change!!, 0.0001)
        org.junit.Assert.assertEquals(220.0, selected.volume!!, 0.0001)
        val day = tickerPeriodStats(TickerPeriod.Day, bars, TickerPrice(12.0, 3.0, 33.3, 350.0))
        org.junit.Assert.assertEquals(3.0, day.change!!, 0.0001)
        org.junit.Assert.assertEquals(33.333, day.percent!!, 0.001)
        org.junit.Assert.assertNull(tickerPeriodStats(TickerPeriod.Month, emptyList(), daily).change)
    }
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
