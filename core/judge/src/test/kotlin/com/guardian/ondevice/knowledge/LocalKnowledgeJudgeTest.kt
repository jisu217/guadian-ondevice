package com.guardian.ondevice.knowledge

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.RiskCategory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalKnowledgeJudgeTest {
    @Test
    fun `returns medium verdict for urgency evidence`() = runTest {
        val verdict = LocalKnowledgeJudge().judge("https://example.com/free-gift")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(Layer.KNOWLEDGE, verdict.layer)
        assertEquals(RiskCategory.URGENCY, verdict.category)
    }

    @Test
    fun `returns high verdict for apk evidence`() = runTest {
        val verdict = LocalKnowledgeJudge().judge("download app apk")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(RiskCategory.APK, verdict.category)
    }

    @Test
    fun `returns uncertain unknown when no local knowledge matches`() = runTest {
        val verdict = LocalKnowledgeJudge().judge("plain article reading page")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(Layer.KNOWLEDGE, verdict.layer)
    }
}
