package com.guardian.ondevice.judge

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalReasoningJudgeTest {
    @Test
    fun `returns first certain local reasoning verdict`() {
        val judge = LocalReasoningJudge(
            listOf(
                fixedJudge("on-device-model", Risk.UNKNOWN, Confidence.UNCERTAIN),
                fixedJudge("local-rag", Risk.MEDIUM, Confidence.CERTAIN),
            )
        )

        val verdict = judgeNow(judge, "https://example.com/free")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals("local-rag", verdict.source)
    }

    @Test
    fun `returns last uncertain verdict when no local layer is certain`() {
        val judge = LocalReasoningJudge(
            listOf(
                fixedJudge("on-device-model", Risk.UNKNOWN, Confidence.UNCERTAIN),
                fixedJudge("local-rag", Risk.UNKNOWN, Confidence.UNCERTAIN),
            )
        )

        val verdict = judgeNow(judge, "https://example.com")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals("local-rag", verdict.source)
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
