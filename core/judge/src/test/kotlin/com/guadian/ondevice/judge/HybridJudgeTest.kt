package com.guadian.ondevice.judge

import com.guadian.ondevice.classifier.OnDeviceModelJudge
import com.guadian.ondevice.knowledge.LocalKnowledgeJudge
import com.guadian.ondevice.rules.ApkRule
import com.guadian.ondevice.rules.BrandImpersonationRule
import com.guadian.ondevice.rules.RuleBasedJudge
import com.guadian.ondevice.rules.SuspiciousUrlRule
import com.guadian.ondevice.store.InMemoryRiskStore
import com.guadian.ondevice.store.StoreJudge
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HybridJudgeTest {
    @Test
    fun `returns the first certain verdict and skips later stages`() = runTest {
        val later = CountingJudge(fixed(Layer.PAGE_CONTENT, Risk.HIGH, Confidence.CERTAIN))
        val judge = HybridJudge(
            stages = listOf(
                CountingJudge(fixed(Layer.RULE, Risk.UNKNOWN, Confidence.UNCERTAIN)),
                CountingJudge(fixed(Layer.MODEL, Risk.LOW, Confidence.CERTAIN)),
                later,
            ),
        )

        val verdict = judge.judge("https://example.com")

        assertEquals(Layer.MODEL, verdict.layer)
        assertEquals(0, later.calls)
    }

    @Test
    fun `returns the last verdict when no stage is certain`() = runTest {
        val judge = HybridJudge(
            stages = listOf(
                CountingJudge(fixed(Layer.RULE, Risk.UNKNOWN, Confidence.UNCERTAIN)),
                CountingJudge(fixed(Layer.PAGE_CONTENT, Risk.UNKNOWN, Confidence.UNCERTAIN)),
            ),
        )

        assertEquals(Layer.PAGE_CONTENT, judge.judge("https://example.com").layer)
    }

    @Test
    fun `requires at least one stage`() {
        assertFailsWith<IllegalArgumentException> { HybridJudge(stages = emptyList()) }
    }

    @Test
    fun `deterministic rules run before a cached verdict can answer`() = runTest {
        // Regression: the store used to be consulted first and keyed by host,
        // so a LOW for "evil.example/" was reused for "evil.example/app.apk".
        val store = InMemoryRiskStore()
        store.save("https://evil.example/app.apk", fixed(Layer.MODEL, Risk.LOW, Confidence.CERTAIN))
        val judge = HybridJudge(
            stages = listOf(RuleBasedJudge(listOf(ApkRule())), StoreJudge(store)),
            store = store,
        )

        val verdict = judge.judge("https://evil.example/app.apk")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Layer.RULE, verdict.layer)
    }

    @Test
    fun `a verdict for one path is not reused for another path on the same host`() = runTest {
        val store = InMemoryRiskStore()
        val model = CountingJudge(fixed(Layer.MODEL, Risk.LOW, Confidence.CERTAIN))
        val judge = HybridJudge(stages = listOf(StoreJudge(store), model), store = store)

        judge.judge("https://evil.example/")
        judge.judge("https://evil.example/other")

        assertEquals(2, model.calls)
    }

    @Test
    fun `caches certain verdicts and answers repeats from the store`() = runTest {
        val store = InMemoryRiskStore()
        val model = CountingJudge(fixed(Layer.MODEL, Risk.LOW, Confidence.CERTAIN))
        val judge = HybridJudge(stages = listOf(StoreJudge(store), model), store = store)

        judge.judge("https://docs.example.com/support?utm_source=ad")
        val second = judge.judge("https://www.docs.example.com/support")

        assertEquals(Layer.STORE, second.layer)
        assertEquals(1, model.calls)
    }

    @Test
    fun `does not cache unknown results`() = runTest {
        val store = InMemoryRiskStore()
        val judge = HybridJudge(
            stages = listOf(StoreJudge(store), CountingJudge(fixed(Layer.PAGE_CONTENT, Risk.UNKNOWN, Confidence.UNCERTAIN))),
            store = store,
        )

        judge.judge("https://example.com")

        assertNull(store.find("https://example.com"))
    }

    @Test
    fun `on-device model resolves a trusted support url before later stages`() = runTest {
        val later = CountingJudge(fixed(Layer.PAGE_CONTENT, Risk.HIGH, Confidence.CERTAIN))

        val verdict = actualLocalHybrid(later).judge("https://docs.example.com/support/chat?lang=ko")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Layer.MODEL, verdict.layer)
        assertEquals(0, later.calls)
    }

    @Test
    fun `local knowledge resolves when rules and model are uncertain`() = runTest {
        val later = CountingJudge(fixed(Layer.PAGE_CONTENT, Risk.HIGH, Confidence.CERTAIN))

        val verdict = actualLocalHybrid(later).judge(
            "https://survey-form.unlisted-check.test/personal-info-phone-card?ref=abcdefghijklmnopqrstuvwxyz1234567890"
        )

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Layer.KNOWLEDGE, verdict.layer)
        assertEquals(RiskCategory.PERSONAL_INFO, verdict.category)
        assertEquals(0, later.calls)
    }

    @Test
    fun `reaches the last stage only when every URL layer is uncertain`() = runTest {
        val later = CountingJudge(fixed(Layer.PAGE_CONTENT, Risk.MEDIUM, Confidence.CERTAIN))

        val verdict = actualLocalHybrid(later).judge("https://plain-article.org/read")

        assertEquals(Layer.PAGE_CONTENT, verdict.layer)
        assertEquals(1, later.calls)
    }

    private fun actualLocalHybrid(later: Judge): HybridJudge {
        val store = InMemoryRiskStore()
        return HybridJudge(
            stages = listOf(
                RuleBasedJudge(listOf(ApkRule(), BrandImpersonationRule(), SuspiciousUrlRule())),
                StoreJudge(store),
                OnDeviceModelJudge(),
                LocalKnowledgeJudge(),
                later,
            ),
            store = store,
        )
    }

    private fun fixed(layer: Layer, risk: Risk, confidence: Confidence) = Verdict(
        risk = risk,
        confidence = confidence,
        layer = layer,
        reason = ReasonCode.NO_RULE_MATCH,
    )

    private class CountingJudge(private val verdict: Verdict) : Judge {
        var calls = 0
            private set

        override suspend fun judge(url: String): Verdict {
            calls += 1
            return verdict
        }
    }
}
