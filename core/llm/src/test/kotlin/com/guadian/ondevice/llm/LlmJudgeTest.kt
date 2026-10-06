package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.verdict.AnswerResult
import com.guadian.ondevice.verdict.Evidence
import com.guadian.ondevice.verdict.VerdictTable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmJudgeTest {
    private val known = Evidence(VerdictTable.Facts())

    private fun graded(result: AnswerResult) = (result as AnswerResult.Graded).verdict

    @Test
    fun `builds the prompt from the posted observation and stays uncertain without a model`() = runTest {
        val board = ObservationBoard()
        val prompts = mutableListOf<String>()
        val judge = LlmJudge(board, onPrompt = { _, prompt -> prompts += prompt })
        board.post("https://down.example/", PageObservation(url = "https://down.example/", loaded = false))

        val verdict = graded(judge.answer("https://down.example/", known))

        assertEquals(listOf("주소: https://down.example/\n페이지: 열리지 않음"), prompts)
        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(Layer.LLM, verdict.layer)
        assertEquals(ReasonCode.LLM_NOT_LOADED, verdict.reason)
    }

    @Test
    fun `a reply that grades the risk is passed on as it is`() = runTest {
        val board = ObservationBoard()
        val obs = PageObservation(url = "https://roblox.com.mu/x", loaded = false)
        val evidence = PromptBuilder().build(obs).lines().first { it.startsWith("주소 흉내:") }
        val runtime = LlmRuntime { """{"risk": "HIGH", "type": "impersonation", "evidence": "$evidence"}""" }
        board.post(obs.url, obs)

        val verdict = graded(LlmJudge(board, runtime).answer(obs.url, known))

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(ReasonCode.LLM_VERDICT, verdict.reason)
        assertEquals("roblox.com.mu", verdict.host)
    }

    @Test
    fun `a question model's answers go to the verdict table`() = runTest {
        val board = ObservationBoard()
        val obs = PageObservation(url = "https://kb-star-bank.top/login", loaded = true, finalUrl = "https://kb-star-bank.top/login",
            status = 200, title = "KB국민은행 본인인증", text = "본인인증", textChars = 4)
        val title = PromptBuilder().build(obs).lines().first { it.startsWith("제목:") }
        val runtime = LlmRuntime {
            """{"claims": "KB국민은행", "evidence": "$title", "sign_in": true, "payment": false, "investment": false,
               "reward_pressure": false, "content_farm": false}"""
        }
        board.post(obs.url, obs)

        val evidence = (LlmJudge(board, runtime).answer(obs.url, known) as AnswerResult.Answered).evidence

        assertEquals(VerdictTable.Claim.LISTED_OTHER, evidence.facts.claim)
        assertTrue(evidence.facts.signIn!!)
        assertEquals("HIGH", VerdictTable.default.decide(evidence.facts.copy(opened = true, official = false,
            apkLink = false, sensitiveField = false, personalField = false)).risk)
    }

    @Test
    fun `an unreadable reply is an uncertain medium`() = runTest {
        val board = ObservationBoard()
        board.post("https://x.example/", PageObservation(url = "https://x.example/", loaded = false))

        val verdict = graded(LlmJudge(board, LlmRuntime { "no json here" }).answer("https://x.example/", known))

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(ReasonCode.LLM_MALFORMED_OUTPUT, verdict.reason)
    }

    @Test
    fun `without an observation it does not build a prompt`() = runTest {
        var built = false
        val verdict = graded(LlmJudge(onPrompt = { _, _ -> built = true }).answer("https://example.com/", known))

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
