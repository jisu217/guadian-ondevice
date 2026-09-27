package com.guadian.ondevice.judge

import com.guadian.ondevice.knowledge.LocalKnowledgeJudge
import com.guadian.ondevice.rules.ApkRule
import com.guadian.ondevice.rules.BrandImpersonationRule
import com.guadian.ondevice.rules.RuleBasedJudge
import com.guadian.ondevice.rules.SuspiciousUrlRule
import com.guadian.ondevice.store.InMemoryVerdictStore
import com.guadian.ondevice.store.StoreJudge
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real URL layers in app order, ahead of a later stage. */
class LocalPipelineTest {
    @Test
    fun `local knowledge resolves when the rules are uncertain`() = runTest {
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
        val store = InMemoryVerdictStore()
        return HybridJudge(
            stages = listOf(
                RuleBasedJudge(listOf(ApkRule(), BrandImpersonationRule(), SuspiciousUrlRule())),
                StoreJudge(store),
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
