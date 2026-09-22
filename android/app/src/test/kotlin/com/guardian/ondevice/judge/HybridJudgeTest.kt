package com.guardian.ondevice.judge

import com.guardian.ondevice.store.LocalRiskStore
import kotlin.test.Test
import kotlin.test.assertEquals

class HybridJudgeTest {
    @Test
    fun `returns cached verdict before running other judges`() {
        val store = LocalRiskStore()
        store.save(
            "https://example.com",
            Verdict(
                risk = Risk.LOW,
                reason = "Cached safe verdict.",
                advice = "Allow.",
                source = "rule:official-domain",
            )
        )
        val hybridJudge = HybridJudge(
            store = store,
            ruleBasedJudge = fixedJudge("rule", Risk.HIGH, Confidence.CERTAIN),
            modelJudge = fixedJudge("model", Risk.HIGH, Confidence.CERTAIN),
            remoteJudge = fixedJudge("remote", Risk.HIGH, Confidence.CERTAIN),
        )

        val verdict = judgeNow(hybridJudge, "https://www.example.com/path")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals("local-store", verdict.source)
    }

    @Test
    fun `uses remote fallback when local judges are uncertain`() {
        val hybridJudge = HybridJudge(
            store = LocalRiskStore(),
            ruleBasedJudge = fixedJudge("rule-engine", Risk.UNKNOWN, Confidence.UNCERTAIN),
            modelJudge = fixedJudge("on-device-model", Risk.UNKNOWN, Confidence.UNCERTAIN),
            remoteJudge = fixedJudge("remote", Risk.MEDIUM, Confidence.CERTAIN),
        )

        val verdict = judgeNow(hybridJudge, "https://example.com/free")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals("remote", verdict.source)
    }

    @Test
    fun `does not cache unknown fallback result`() {
        val store = LocalRiskStore()
        val hybridJudge = HybridJudge(
            store = store,
            ruleBasedJudge = fixedJudge("rule-engine", Risk.UNKNOWN, Confidence.UNCERTAIN),
            modelJudge = fixedJudge("on-device-model", Risk.UNKNOWN, Confidence.UNCERTAIN),
            remoteJudge = fixedJudge("remote", Risk.UNKNOWN, Confidence.UNCERTAIN),
        )

        judgeNow(hybridJudge, "https://example.com")

        assertEquals(null, store.find("https://example.com"))
    }

    private fun fixedJudge(source: String, risk: Risk, confidence: Confidence): Judge {
        return object : Judge {
            override fun judge(url: String, done: (Verdict) -> Unit) {
                done(
                    Verdict(
                        risk = risk,
                        reason = "$source verdict.",
                        advice = "Continue flow.",
                        source = source,
                        confidence = confidence,
                    )
                )
            }
        }
    }

    private fun judgeNow(judge: Judge, url: String): Verdict {
        lateinit var verdict: Verdict
        judge.judge(url) { verdict = it }
        return verdict
    }
}
