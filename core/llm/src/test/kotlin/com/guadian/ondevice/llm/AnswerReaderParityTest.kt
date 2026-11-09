package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AnswerReader must read replies exactly as ml/guadian_ml/llm/contract/answer.py does, so
 * offline scores match what the app shows. Fixture: data/fixtures/llm_answer_cases.jsonl.
 */
class AnswerReaderParityTest {
    @Test
    fun `matches the python answer fixture`() {
        val rows = File("../../ml/fixtures/llm/answer_cases.jsonl").readLines()
            .filter { it.isNotBlank() }.map(::JSONObject)
        assertTrue(rows.size >= 10)
        for (row in rows) {
            val reply = row.getString("reply")
            val v = AnswerReader.read(reply, row.getString("prompt"))
            assertEquals(row.getString("risk"), v.risk.name, reply)
            assertEquals(row.getBoolean("certain"), v.confidence == Confidence.CERTAIN, reply)
            assertEquals(row.getString("reason"), v.reason.name, reply)
        }
    }

    @Test
    fun `matches the python question answer fixture`() {
        val rows = File("../../ml/fixtures/llm/question_answer_cases.jsonl").readLines()
            .filter { it.isNotBlank() }.map(::JSONObject)
        assertTrue(rows.size >= 10)
        for (row in rows) {
            val reply = row.getString("reply")
            val prompt = row.getString("prompt")
            assertEquals(row.getBoolean("graded"), AnswerReader.isGraded(reply), reply)
            val actual = AnswerReader.readAnswers(reply, prompt)
            if (row.isNull("answers")) {
                assertEquals(null, actual, reply)
                continue
            }
            val e = row.getJSONObject("answers")
            val expected = AnswerReader.Answers(
                claims = if (e.isNull("claims")) null else e.getString("claims"),
                evidence = e.getString("evidence"),
                signIn = e.getBoolean("sign_in"),
                payment = e.getBoolean("payment"),
                investment = e.getBoolean("investment"),
                rewardPressure = e.getBoolean("reward_pressure"),
                contentFarm = e.getBoolean("content_farm"),
            )
            assertEquals(expected, actual, reply)
        }
    }
}
