package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class AnswerReaderTest {
    private val prompt = listOf(
        "주소: https://kb-star-bank.top/login",
        "페이지: 상태 200, 본문 300자",
        "브랜드 언급: 국민은행(공식 kbstar.com) / 실제 kb-star-bank.top",
        "입력 칸: password, rrn (외부로 전송)",
    ).joinToString("\n")

    private fun read(reply: String?) = AnswerReader.read(reply, prompt)

    @Test
    fun `a grounded high verdict is kept`() {
        val v = read("""{"risk": "HIGH", "type": "impersonation", "evidence": "브랜드 언급: 국민은행(공식 kbstar.com) / 실제 kb-star-bank.top"}""")

        assertEquals(Risk.HIGH, v.risk)
        assertEquals(Confidence.CERTAIN, v.confidence)
        assertEquals(ReasonCode.LLM_VERDICT, v.reason)
        assertEquals(RiskCategory.BRAND_IMPERSONATION, v.category)
    }

    @Test
    fun `high without a quoted prompt line becomes medium`() {
        for (evidence in listOf("", "the page asks for a password", "본문: 존재하지 않는 문장")) {
            val v = read("""{"risk": "HIGH", "type": "credentials", "evidence": "$evidence"}""")
            assertEquals(Risk.MEDIUM, v.risk, evidence)
            assertEquals(ReasonCode.LLM_UNSUPPORTED_HIGH, v.reason, evidence)
        }
    }

    @Test
    fun `high of a type the criteria do not allow becomes medium`() {
        val v = read("""{"risk": "HIGH", "type": "urgency", "evidence": "입력 칸: password, rrn (외부로 전송)"}""")

        assertEquals(Risk.MEDIUM, v.risk)
        assertEquals(ReasonCode.LLM_UNSUPPORTED_HIGH, v.reason)
    }

    @Test
    fun `unreadable replies are uncertain medium`() {
        val replies = listOf(null, "", "no json", """{"risk": "중간"}""", """{"risk": "LOW", "type": "phishing"}""",
            """{"risk": "UNKNOWN", "type": "none"}""", """{"risk": "HIGH", "type": "apk", "evidence": "cut off""")
        for (reply in replies) {
            val v = read(reply)
            assertEquals(Risk.MEDIUM, v.risk, reply)
            assertEquals(Confidence.UNCERTAIN, v.confidence, reply)
            assertEquals(ReasonCode.LLM_MALFORMED_OUTPUT, v.reason, reply)
        }
    }

    @Test
    fun `text around the object and braces inside strings are tolerated`() {
        val v = read("""```json
            {"risk": "MEDIUM", "type": "personal_info", "evidence": "form {name}"}
            ``` trailing""")

        assertEquals(Risk.MEDIUM, v.risk)
        assertEquals(ReasonCode.LLM_VERDICT, v.reason)
        assertEquals(RiskCategory.PERSONAL_INFO, v.category)
    }

    @Test
    fun `low always carries no category`() {
        assertEquals(RiskCategory.NONE, read("""{"risk": "LOW", "type": "personal_info", "evidence": ""}""").category)
    }
}
