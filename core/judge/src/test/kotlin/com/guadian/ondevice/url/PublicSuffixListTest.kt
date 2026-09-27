package com.guadian.ondevice.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicSuffixListTest {
    private val psl = PublicSuffixList.default

    @Test
    fun `resolves ordinary and two-level suffixes from the bundled list`() {
        assertEquals("nate.com", psl.registrableDomain("m.news.nate.com"))
        assertEquals("kbstar.co.kr", psl.registrableDomain("m.kbstar.co.kr"))
        assertEquals("example.co.uk", psl.registrableDomain("a.b.example.co.uk"))
    }

    @Test
    fun `treats private-section suffixes as separate sites`() {
        assertEquals("a.github.io", psl.registrableDomain("x.a.github.io"))
    }

    @Test
    fun `returns null for a bare public suffix and the host for ip literals`() {
        assertNull(psl.registrableDomain("co.kr"))
        assertEquals("10.0.2.2", psl.registrableDomain("10.0.2.2"))
    }

    @Test
    fun `applies wildcard and exception rules`() {
        val list = PublicSuffixList(sequenceOf("*.ck", "!www.ck", "com"))

        assertEquals("b.a.ck", list.registrableDomain("x.b.a.ck"))
        assertEquals("www.ck", list.registrableDomain("x.www.ck"))
        assertEquals("example.com", list.registrableDomain("a.example.com"))
    }

    @Test
    fun `unlisted tlds fall back to the implicit star rule`() {
        assertEquals("example.test", PublicSuffixList(emptySequence()).registrableDomain("a.example.test"))
    }
}
