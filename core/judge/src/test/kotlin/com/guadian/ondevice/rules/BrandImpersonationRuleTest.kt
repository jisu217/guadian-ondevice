package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.util.DomainAllowlist
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
        assertEquals(ReasonCode.OFFICIAL_DOMAIN, verdict.reason)
    }

    @Test
    fun officialDomainsThatEmbedABrandLabelAreLowRisk() {
        // Regression: substring matching flagged these as HIGH impersonation.
        for (url in listOf("https://www.kakaobank.com", "https://naver.me/abc", "https://shinhansec.com")) {
            assertEquals(Risk.LOW, rule.evaluate(url)!!.risk, url)
        }
    }

    @Test
    fun brandTokenOnNonOfficialDomainIsHighRisk() {
        val verdict = rule.evaluate("https://naver-login.example.com")!!

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(RiskCategory.BRAND_IMPERSONATION, verdict.category)
        assertEquals("naver", verdict.detail)
    }

    @Test
    fun brandInsideALongerHostTokenIsOnlyAnUncertainHint() {
        val verdict = rule.evaluate("https://mykakaopromo.xyz")!!

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(ReasonCode.BRAND_SUBSTRING_IN_HOST, verdict.reason)
    }

    @Test
    fun brandSubstringOnAnAllowlistedSiteIsIgnored() {
        val rule = BrandImpersonationRule(allowlist = DomainAllowlist(setOf("kakaogames.com")))

        assertNull(rule.evaluate("https://kakaogames.com"))
    }

    @Test
    fun allowlistIsCheckedOnTheRegistrableDomainNotAParentSuffix() {
        // github.io is a public suffix: "x.github.io" must not inherit its listing.
        val rule = BrandImpersonationRule(allowlist = DomainAllowlist(setOf("github.io")))

        assertEquals(Risk.MEDIUM, rule.evaluate("https://mykakaopromo.github.io")!!.risk)
    }

    @Test
    fun unrelatedHostIsNotEvaluated() {
        assertNull(rule.evaluate("https://example.org/about"))
    }
}
