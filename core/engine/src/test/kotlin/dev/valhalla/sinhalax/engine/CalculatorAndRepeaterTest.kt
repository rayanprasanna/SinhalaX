package dev.valhalla.sinhalax.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalculatorAndRepeaterTest {

    @Test
    fun basics() {
        assertEquals("1050", Calculator.result("250×4+50"))
        assertEquals("1050", Calculator.result("250*4+50"))
        assertEquals("0.3", Calculator.result("0.1+0.2"))
        assertEquals("2.5", Calculator.result("5÷2"))
        assertEquals("14", Calculator.result("2×(3+4)"))
        assertEquals("-5", Calculator.result("−2−3"))
        assertEquals("1000", Calculator.result("1,000"))
    }

    @Test
    fun percent() {
        assertEquals("0.5", Calculator.result("50%"))
        assertEquals("220", Calculator.result("200+10%"))
        assertEquals("180", Calculator.result("200-10%"))
        assertEquals("20", Calculator.result("200×10%"))
    }

    @Test
    fun invalid() {
        assertNull(Calculator.result(""))
        assertNull(Calculator.result("5+"))
        assertNull(Calculator.result("5÷0"))
        assertNull(Calculator.result("(2+3"))
        assertNull(Calculator.result("1..2"))
    }

    @Test
    fun repeater() {
        assertEquals("hi hi hi", TextRepeater.repeat("hi", 3))
        assertEquals("1. ආදරෙයි\n2. ආදරෙයි", TextRepeater.repeat("ආදරෙයි", 2, TextRepeater.Joiner.NEW_LINE, numbered = true))
        assertEquals("", TextRepeater.repeat("  ", 5))
        val long = TextRepeater.repeat("abcdefghij", 10_000, TextRepeater.Joiner.NONE)
        assertEquals(TextRepeater.MAX_COUNT * 10, long.length)
    }
}
