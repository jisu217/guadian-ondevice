package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrandImpersonationRuleTest {
    private val rule = BrandImpersonationRule()

    @Test
    fun officialDomainIsLowRiskRegardlessOfHostCase() {
        // Regression: hostOf() used to skip lowercasing, so an official
        // domain typed/rendered in uppercase failed the exact-match check
        // and fell through to being flagged as brand impersonation instead.
        val verdict = rule.evaluate("https://PAY.NAVER.COM")!!

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals("rule:official-domain", verdict.source)
    }

    @Test
    fun brandMentionOnNonOfficialDomainIsHighRisk() {
        val verdict = rule.evaluate("https://naver-login.example.com")!!

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("brand_impersonation", verdict.type)
    }

    @Test
    fun unrelatedHostIsNotEvaluated() {
        assertNull(rule.evaluate("https://example.org/about"))
    }
}
