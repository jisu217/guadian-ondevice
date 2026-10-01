package com.guadian.ondevice.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlHostTest {
    @Test
    fun lowercasesTheHost() {
        assertEquals("pay.naver.com", UrlHost.of("https://PAY.NAVER.COM/login"))
    }

    @Test
    fun stripsAWwwPrefix() {
        assertEquals("example.com", UrlHost.of("https://www.example.com/path"))
    }

    @Test
    fun addsAHttpsSchemeWhenMissing() {
        assertEquals("example.com", UrlHost.of("example.com/path"))
    }

    @Test
    fun returnsNullForBlankOrUnparsableInput() {
        assertNull(UrlHost.of(null))
        assertNull(UrlHost.of(""))
        assertNull(UrlHost.of("not a url"))
    }
}
