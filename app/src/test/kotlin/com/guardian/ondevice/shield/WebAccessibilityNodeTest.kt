package com.guardian.ondevice.shield

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Covers only the pure string logic (hostOf/siteOf/sameSite). The
// AccessibilityNodeInfo-based functions (role/isLink/canBearUrl/targetHost)
// need a real accessibility tree from a browser and are not covered by
// automated tests -- see docs/architecture.md's Shield section for the
// manual emulator verification steps.
class WebAccessibilityNodeTest {

    @Test
    fun hostOfParsesAFullUrl() {
        assertEquals("m.example.com", WebAccessibilityNode.hostOf("https://m.example.com/path?x=1"))
    }

    @Test
    fun hostOfAddsSchemeWhenMissing() {
        assertEquals("example.com", WebAccessibilityNode.hostOf("example.com/path"))
    }

    @Test
    fun hostOfReturnsNullForBlankOrUnparsable() {
        assertNull(WebAccessibilityNode.hostOf(null))
        assertNull(WebAccessibilityNode.hostOf(""))
        assertNull(WebAccessibilityNode.hostOf("not a url"))
    }

    @Test
    fun siteOfKeepsTwoLabelsForOrdinaryDomains() {
        assertEquals("nate.com", WebAccessibilityNode.siteOf("m.news.nate.com"))
        assertEquals("nate.com", WebAccessibilityNode.siteOf("nate.com"))
    }

    @Test
    fun siteOfKeepsThreeLabelsForTwoLevelSuffixes() {
        assertEquals("kbstar.co.kr", WebAccessibilityNode.siteOf("m.kbstar.co.kr"))
    }

    @Test
    fun sameSiteIgnoresSubdomainDifferences() {
        assertTrue(WebAccessibilityNode.sameSite("m.news.nate.com", "www.nate.com"))
    }

    @Test
    fun sameSiteDetectsADifferentSite() {
        assertFalse(WebAccessibilityNode.sameSite("nate.com", "coupang.com"))
    }

    @Test
    fun sameSiteIsFalseWhenEitherSideIsUnknown() {
        assertFalse(WebAccessibilityNode.sameSite(null, "nate.com"))
        assertFalse(WebAccessibilityNode.sameSite("nate.com", null))
    }
}
