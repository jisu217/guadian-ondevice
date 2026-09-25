package com.guardian.ondevice.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainAllowlistTest {
    private val allowlist = DomainAllowlist(setOf("naver.com", "Example.com"))

    @Test
    fun `covers listed domains and their subdomains only`() {
        assertTrue(allowlist.coversHost("naver.com"))
        assertTrue(allowlist.coversHost("m.pay.naver.com"))
        assertTrue(allowlist.coversHost("example.com"))
        assertFalse(allowlist.coversHost("xnaver.com"))
        assertFalse(allowlist.coversHost("naver.com.evil.example"))
        assertFalse(allowlist.coversHost(""))
    }

    @Test
    fun `containsExactly does not walk parent domains`() {
        assertTrue(allowlist.containsExactly("naver.com"))
        assertFalse(allowlist.containsExactly("m.naver.com"))
    }

    @Test
    fun `bundled allowlist includes every brand domain`() {
        assertTrue(DomainAllowlist.default.containsExactly("kakaobank.com"))
        assertTrue(DomainAllowlist.default.containsExactly("naver.me"))
    }
}
