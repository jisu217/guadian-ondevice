package com.guadian.ondevice.judge

import com.guadian.ondevice.rules.ApkRule
import com.guadian.ondevice.rules.RuleBasedJudge
import com.guadian.ondevice.store.InMemoryVerdictStore
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
                CountingJudge(fixed(Layer.KNOWLEDGE, Risk.LOW, Confidence.CERTAIN)),
                later,
            ),
        )

        val verdict = judge.judge("https://example.com")

        assertEquals(Layer.KNOWLEDGE, verdict.layer)
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
        val store = InMemoryVerdictStore()
        store.save("https://evil.example/app.apk", fixed(Layer.KNOWLEDGE, Risk.LOW, Confidence.CERTAIN))
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
        val store = InMemoryVerdictStore()
        val knowledge = CountingJudge(fixed(Layer.KNOWLEDGE, Risk.LOW, Confidence.CERTAIN))
        val judge = HybridJudge(stages = listOf(StoreJudge(store), knowledge), store = store)

        judge.judge("https://evil.example/")
        judge.judge("https://evil.example/other")

        assertEquals(2, knowledge.calls)
    }

    @Test
    fun `caches certain verdicts and answers repeats from the store`() = runTest {
        val store = InMemoryVerdictStore()
        val knowledge = CountingJudge(fixed(Layer.KNOWLEDGE, Risk.LOW, Confidence.CERTAIN))
        val judge = HybridJudge(stages = listOf(StoreJudge(store), knowledge), store = store)

        judge.judge("https://docs.example.com/support?utm_source=ad")
        val second = judge.judge("https://www.docs.example.com/support")

        assertEquals(Layer.STORE, second.layer)
        assertEquals(1, knowledge.calls)
    }

    @Test
    fun `does not cache unknown results`() = runTest {
        val store = InMemoryVerdictStore()
        val judge = HybridJudge(
            stages = listOf(StoreJudge(store), CountingJudge(fixed(Layer.PAGE_CONTENT, Risk.UNKNOWN, Confidence.UNCERTAIN))),
            store = store,
        )

        judge.judge("https://example.com")

        assertNull(store.find("https://example.com"))
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
