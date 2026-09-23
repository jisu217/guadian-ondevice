package com.guardian.ondevice.model

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class OnDeviceModelJudgeTest {
    @Test
    fun `predicts low risk for trusted support url`() {
        val verdict = judgeNow("https://support.example.com/help")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("on-device-model", verdict.source)
        assertEquals("nearest_centroid", verdict.type)
    }

    @Test
    fun `predicts medium risk for reward event url`() {
        val verdict = judgeNow("https://promo-reward-event.test/free-gift")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
    }

    @Test
    fun `predicts high risk for brand login verification url`() {
        val verdict = judgeNow("https://bank-login-verification.test/account")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
    }

    @Test
    fun `keeps unknown model label uncertain`() {
        val verdict = judgeNow("https://example.org/about")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
    }

    private fun judgeNow(url: String): Verdict {
        lateinit var verdict: Verdict
        OnDeviceModelJudge().judge(url) { verdict = it }
        return verdict
    }
}
