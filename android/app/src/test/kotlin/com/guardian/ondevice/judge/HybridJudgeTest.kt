package com.guardian.ondevice.judge

import com.guardian.ondevice.model.OnDeviceModelJudge
import com.guardian.ondevice.rag.LocalRagJudge
import com.guardian.ondevice.rules.ApkRule
import com.guardian.ondevice.rules.BrandImpersonationRule
import com.guardian.ondevice.rules.SuspiciousUrlRule
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
            localReasoningJudge = fixedJudge("local-reasoning", Risk.HIGH, Confidence.CERTAIN),
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
            localReasoningJudge = fixedJudge("local-reasoning", Risk.UNKNOWN, Confidence.UNCERTAIN),
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
            localReasoningJudge = fixedJudge("local-reasoning", Risk.UNKNOWN, Confidence.UNCERTAIN),
            remoteJudge = fixedJudge("remote", Risk.UNKNOWN, Confidence.UNCERTAIN),
        )

        judgeNow(hybridJudge, "https://example.com")

        assertEquals(null, store.find("https://example.com"))
    }

    @Test
    fun `uses on device model before remote fallback`() {
        val remoteJudge = CountingJudge(fixedVerdict("remote", Risk.HIGH, Confidence.CERTAIN))
        val hybridJudge = actualLocalHybrid(remoteJudge = remoteJudge)

        val verdict = judgeNow(hybridJudge, "https://docs.example.com/status/incidents/2026")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("on-device-model", verdict.source)
        assertEquals(0, remoteJudge.calls)
    }

    @Test
    fun `uses local rag when model is uncertain`() {
        val remoteJudge = CountingJudge(fixedVerdict("remote", Risk.HIGH, Confidence.CERTAIN))
        val hybridJudge = actualLocalHybrid(remoteJudge = remoteJudge)

        val verdict = judgeNow(hybridJudge, "https://survey-form.unlisted-check.test/personal-info-phone-card?ref=abcdefghijklmnopqrstuvwxyz1234567890")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("local-rag", verdict.source)
        assertEquals("personal-info", verdict.type)
        assertEquals(0, remoteJudge.calls)
    }

    @Test
    fun `uses remote only when rule model and rag are uncertain`() {
        val remoteJudge = CountingJudge(fixedVerdict("remote", Risk.MEDIUM, Confidence.CERTAIN))
        val hybridJudge = actualLocalHybrid(remoteJudge = remoteJudge)

        val verdict = judgeNow(hybridJudge, "https://plain-article.org/read")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals("remote", verdict.source)
        assertEquals(1, remoteJudge.calls)
    }

    @Test
    fun `caches reusable local model verdict`() {
        val store = LocalRiskStore()
        val remoteJudge = CountingJudge(fixedVerdict("remote", Risk.HIGH, Confidence.CERTAIN))
        val hybridJudge = actualLocalHybrid(store = store, remoteJudge = remoteJudge)

        val first = judgeNow(hybridJudge, "https://docs.example.com/status/incidents/2026")
        val second = judgeNow(hybridJudge, "https://www.docs.example.com/other-path")

        assertEquals(Risk.LOW, first.risk)
        assertEquals("on-device-model", first.source)
        assertEquals(Risk.LOW, second.risk)
        assertEquals("local-store", second.source)
        assertEquals(0, remoteJudge.calls)
    }

    private fun actualLocalHybrid(
        store: LocalRiskStore = LocalRiskStore(),
        remoteJudge: Judge,
    ): HybridJudge {
        return HybridJudge(
            store = store,
            ruleBasedJudge = RuleBasedJudge(
                rules = listOf(
                    ApkRule(),
                    BrandImpersonationRule(),
                    SuspiciousUrlRule(),
                )
            ),
            localReasoningJudge = LocalReasoningJudge(
                judges = listOf(
                    OnDeviceModelJudge(),
                    LocalRagJudge(),
                )
            ),
            remoteJudge = remoteJudge,
        )
    }

    private fun fixedJudge(source: String, risk: Risk, confidence: Confidence): Judge {
        return object : Judge {
            override fun judge(url: String, done: (Verdict) -> Unit) {
                done(fixedVerdict(source, risk, confidence))
            }
        }
    }

    private fun fixedVerdict(source: String, risk: Risk, confidence: Confidence): Verdict {
        return Verdict(
            risk = risk,
            reason = "$source verdict.",
            advice = "Continue flow.",
            source = source,
            confidence = confidence,
        )
    }

    private class CountingJudge(private val verdict: Verdict) : Judge {
        var calls = 0
            private set

        override fun judge(url: String, done: (Verdict) -> Unit) {
            calls += 1
            done(verdict)
        }
    }

    private fun judgeNow(judge: Judge, url: String): Verdict {
        lateinit var verdict: Verdict
        judge.judge(url) { verdict = it }
        return verdict
    }
}
