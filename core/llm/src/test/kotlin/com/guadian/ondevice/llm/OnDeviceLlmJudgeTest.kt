package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnDeviceLlmJudgeTest {
    @Test
    fun `builds the prompt from the posted observation and stays uncertain`() = runTest {
        val board = ObservationBoard()
        val prompts = mutableListOf<String>()
        val judge = OnDeviceLlmJudge(board, onPrompt = { _, prompt -> prompts += prompt })
        board.post("https://down.example/", PageObservation(url = "https://down.example/", loaded = false))

        val verdict = judge.judge("https://down.example/")

        assertEquals(listOf("주소: https://down.example/\n페이지: 열리지 않음"), prompts)
        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(Layer.LLM, verdict.layer)
        assertEquals(ReasonCode.LLM_NOT_LOADED, verdict.reason)
    }

    @Test
    fun `reads the model's reply against the prompt`() = runTest {
        val board = ObservationBoard()
        val obs = PageObservation(url = "https://roblox.com.mu/x", loaded = false)
        val evidence = PromptBuilder().build(obs).lines().first { it.startsWith("주소 흉내:") }
        val runtime = LlmRuntime { """{"risk": "HIGH", "type": "impersonation", "evidence": "$evidence"}""" }
        board.post(obs.url, obs)

        val verdict = OnDeviceLlmJudge(board, runtime).judge(obs.url)

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(ReasonCode.LLM_VERDICT, verdict.reason)
        assertEquals("roblox.com.mu", verdict.host)
    }

    @Test
    fun `without an observation it does not build a prompt`() = runTest {
        var built = false
        val verdict = OnDeviceLlmJudge(onPrompt = { _, _ -> built = true }).judge("https://example.com/")

        assertEquals(ReasonCode.LLM_NO_OBSERVATION, verdict.reason)
        assertTrue(!built)
    }

    @Test
    fun `an observation is taken once and old ones are dropped`() {
        val board = ObservationBoard(capacity = 2)
        board.post("a", PageObservation(url = "a", loaded = false))
        board.post("b", PageObservation(url = "b", loaded = false))
        board.post("c", PageObservation(url = "c", loaded = false))

        assertNull(board.take("a"))
        assertEquals("b", board.take("b")?.url)
        assertNull(board.take("b"))
    }
}
