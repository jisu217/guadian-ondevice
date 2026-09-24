package com.guardian.ondevice.knowledge

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalKnowledgeJudgeTest {
    @Test
    fun `returns medium verdict for urgency evidence`() {
        val judge = LocalKnowledgeJudge()

        val verdict = judgeNow(judge, "https://example.com/free-gift")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("local-knowledge", verdict.source)
        assertEquals("urgency", verdict.type)
    }

    @Test
    fun `returns high verdict for apk evidence`() {
        val judge = LocalKnowledgeJudge()

        val verdict = judgeNow(judge, "download app apk")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("apk", verdict.type)
    }

    @Test
    fun `returns uncertain unknown when no local knowledge matches`() {
        val judge = LocalKnowledgeJudge()

        val verdict = judgeNow(judge, "plain article reading page")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("local-knowledge", verdict.source)
    }

    private fun judgeNow(judge: LocalKnowledgeJudge, url: String): Verdict {
        lateinit var verdict: Verdict
        judge.judge(url) { verdict = it }
        return verdict
    }
}
