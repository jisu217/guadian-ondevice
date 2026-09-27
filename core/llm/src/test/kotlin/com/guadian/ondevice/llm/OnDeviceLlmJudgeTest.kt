package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OnDeviceLlmJudgeTest {
    @Test
    fun `answers uncertain unknown until a model is loaded`() = runTest {
        val verdict = OnDeviceLlmJudge().judge("https://example.com/login")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(Layer.LLM, verdict.layer)
        assertEquals(ReasonCode.LLM_NOT_LOADED, verdict.reason)
    }
}
