package com.guardian.ondevice.rag

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalRagJudgeTest {
    @Test
    fun `returns medium verdict for urgency evidence`() {
        val judge = LocalRagJudge()

        val verdict = judgeNow(judge, "https://example.com/free-gift")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("local-rag", verdict.source)
        assertEquals("urgency", verdict.type)
    }

    @Test
    fun `returns high verdict for apk evidence`() {
        val judge = LocalRagJudge()

        val verdict = judgeNow(judge, "download app apk")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("apk", verdict.type)
    }

    @Test
    fun `returns uncertain unknown when no local knowledge matches`() {
        val judge = LocalRagJudge()

        val verdict = judgeNow(judge, "plain article reading page")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("local-rag", verdict.source)
    }

    private fun judgeNow(judge: LocalRagJudge, url: String): Verdict {
        lateinit var verdict: Verdict
        judge.judge(url) { verdict = it }
        return verdict
    }
}
