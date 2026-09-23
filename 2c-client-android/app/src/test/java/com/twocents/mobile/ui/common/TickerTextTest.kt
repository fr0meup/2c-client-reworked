package com.twocents.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TickerTextTest {
    @Test fun bareDollarOpensPickerWithoutQuery() {
        assertEquals(TickerContext(4, ""), tickerContext("Buy \$"))
        assertNull(tickerContext("\$AAPL "))
    }

    @Test fun symbolsAreUniqueAndIgnoreCurrencyAmounts() {
        assertEquals(listOf("AAPL", "BRK.B"), tickerSymbols("\$AAPL \$16 \$brk.b \$AAPL"))
        assertEquals(listOf("MSFT"), tickerSymbols("https://example.com/\$AAPL \$MSFT"))
    }
}
