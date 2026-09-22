package com.guardian.ondevice.rag

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalRagJudgeTest {
    @Test
    fun `returns local rag evidence as uncertain verdict`() {
        val judge = LocalRagJudge()

        val verdict = judgeNow(judge, "https://example.com/free-gift")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("local-rag", verdict.source)
        assertEquals("urgency", verdict.type)
    }

    private fun judgeNow(judge: LocalRagJudge, url: String): Verdict {
        lateinit var verdict: Verdict
        judge.judge(url) { verdict = it }
        return verdict
    }
}
