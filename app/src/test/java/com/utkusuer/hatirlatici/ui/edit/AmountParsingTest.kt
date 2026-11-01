package com.utkusuer.hatirlatici.ui.edit

import com.utkusuer.hatirlatici.ui.edit.EditPaymentViewModel.Companion.formatAmountInput
import com.utkusuer.hatirlatici.ui.edit.EditPaymentViewModel.Companion.parseAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParsingTest {
    @Test fun turkishFormats() {
        assertEquals(125050L, parseAmount("1.250,50"))
        assertEquals(125050L, parseAmount("1250,5"))
        assertEquals(125000L, parseAmount("1.250"))
        assertEquals(1250000000L, parseAmount("12.500.000"))
        assertEquals(125000L, parseAmount("1250"))
        assertEquals(125050L, parseAmount("1250.50"))
        assertEquals(125000L, parseAmount("₺ 1.250"))
    }

    @Test fun invalid() {
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("0"))
        assertNull(parseAmount("-5"))
    }

    @Test fun roundTrip() {
        assertEquals("1250", formatAmountInput(125000))
        assertEquals("1250,50", formatAmountInput(125050))
        assertEquals(125050L, parseAmount(formatAmountInput(125050)))
    }
}
