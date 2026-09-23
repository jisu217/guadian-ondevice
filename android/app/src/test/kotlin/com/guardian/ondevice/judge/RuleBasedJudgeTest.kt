package com.guardian.ondevice.judge

import com.guardian.ondevice.rules.ApkRule
import com.guardian.ondevice.rules.BrandImpersonationRule
import com.guardian.ondevice.rules.SuspiciousUrlRule
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleBasedJudgeTest {
    private val judge = RuleBasedJudge(
        rules = listOf(
            ApkRule(),
            BrandImpersonationRule(),
            SuspiciousUrlRule(),
        )
    )

    @Test
    fun `apk download is high risk`() {
        val verdict = judgeNow("https://download.example.com/app.apk")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("rule:apk", verdict.source)
    }

    @Test
    fun `official domain is low risk`() {
        val verdict = judgeNow("https://pay.naver.com")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("rule:official-domain", verdict.source)
    }

    @Test
    fun `brand mention on non official domain is high risk`() {
        val verdict = judgeNow("https://naver-login.example.com")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("rule:brand-impersonation", verdict.source)
    }

    @Test
    fun `suspicious keyword is medium risk`() {
        val verdict = judgeNow("https://example.com/free-gift")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("rule:suspicious-url", verdict.source)
    }

    @Test
    fun `unknown url remains uncertain`() {
        val verdict = judgeNow("https://example.org/about")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("rule-engine", verdict.source)
    }

    @Test
    fun `unrelated host containing a short brand code is not impersonation`() {
        val verdict = judgeNow("https://bankbook.com/about")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals("rule-engine", verdict.source)
    }

    @Test
    fun `suspicious keyword in the host only is not flagged`() {
        val verdict = judgeNow("https://myaccount.google.com/security")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals("rule-engine", verdict.source)
    }

    private fun judgeNow(url: String): Verdict {
        lateinit var verdict: Verdict
        judge.judge(url) { verdict = it }
        return verdict
    }
}
