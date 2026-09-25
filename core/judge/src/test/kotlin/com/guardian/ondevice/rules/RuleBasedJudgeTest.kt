package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.RiskCategory
import com.guardian.ondevice.judge.Verdict
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleBasedJudgeTest {
    private val judge = RuleBasedJudge(listOf(ApkRule(), BrandImpersonationRule(), SuspiciousUrlRule()))

    @Test
    fun `apk download is high risk`() = runTest {
        val verdict = judge.judge("https://download.example.com/app.apk")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(RiskCategory.APK, verdict.category)
    }

    @Test
    fun `official domain is low risk`() = runTest {
        val verdict = judge.judge("https://pay.naver.com")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(ReasonCode.OFFICIAL_DOMAIN, verdict.reason)
    }

    @Test
    fun `brand token on non official domain is high risk`() = runTest {
        val verdict = judge.judge("https://naver-login.example.com")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(ReasonCode.BRAND_TOKEN_IN_HOST, verdict.reason)
    }

    @Test
    fun `suspicious keyword is medium risk`() = runTest {
        val verdict = judge.judge("https://example.com/free-gift")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(RiskCategory.SUSPICIOUS_URL, verdict.category)
    }

    @Test
    fun `unknown url remains uncertain`() = runTest {
        val verdict = judge.judge("https://example.org/about")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(ReasonCode.NO_RULE_MATCH, verdict.reason)
    }

    @Test
    fun `unrelated host containing a short brand code is not impersonation`() = runTest {
        assertEquals(ReasonCode.NO_RULE_MATCH, judge.judge("https://bankbook.com/about").reason)
    }

    @Test
    fun `suspicious keyword in the host only is not flagged`() = runTest {
        assertEquals(ReasonCode.NO_RULE_MATCH, judge.judge("https://myaccount.google.com/security").reason)
    }

    @Test
    fun `keyword inside a longer path word is not flagged`() = runTest {
        // Regression: substring matching read "/prevention" as "event".
        assertEquals(ReasonCode.NO_RULE_MATCH, judge.judge("https://example.com/prevention").reason)
        assertEquals(ReasonCode.NO_RULE_MATCH, judge.judge("https://example.com/accounting").reason)
    }

    @Test
    fun `a certain rule wins over an earlier uncertain hint`() = runTest {
        val hint = rule(Risk.MEDIUM, Confidence.UNCERTAIN)
        val certain = rule(Risk.HIGH, Confidence.CERTAIN)

        val verdict = RuleBasedJudge(listOf(hint, certain)).judge("https://example.com")

        assertEquals(Risk.HIGH, verdict.risk)
    }

    @Test
    fun `an uncertain hint is returned when no rule is certain`() = runTest {
        val verdict = RuleBasedJudge(listOf(rule(Risk.MEDIUM, Confidence.UNCERTAIN))).judge("https://example.com")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
    }

    private fun rule(risk: Risk, confidence: Confidence) = object : RiskRule {
        override fun evaluate(url: String) = Verdict(
            risk = risk,
            confidence = confidence,
            layer = Layer.RULE,
            reason = ReasonCode.SUSPICIOUS_KEYWORD,
        )
    }
}
