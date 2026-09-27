package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AnswerReader must read replies exactly as model/llm/answer_reader.py does, so
 * offline scores match what the app shows. Fixture: data/fixtures/llm_answer_cases.jsonl.
 */
class AnswerReaderParityTest {
    @Test
    fun `matches the python answer fixture`() {
        val rows = File("../../model/data/fixtures/llm_answer_cases.jsonl").readLines()
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
}
