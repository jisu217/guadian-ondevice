package com.guadian.ondevice.llm

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The prompt the app builds must equal the one the model was trained on.
 * ml/fixtures/llm/prompt_cases.jsonl is written by guadian_ml.llm.contract.fixtures.
 */
class PromptBuilderParityTest {
    private val builder = PromptBuilder()

    @Test
    fun `matches the python prompt fixture`() {
        val rows = File("../../ml/fixtures/llm/prompt_cases.jsonl").readLines().filter { it.isNotBlank() }
        assertTrue(rows.size >= 10)
        for (row in rows.map(::JSONObject)) {
            val obs = observationFromJson(row.getJSONObject("observation"))
            assertEquals(row.getString("prompt"), builder.build(obs), obs.url)
        }
    }

    /**
     * The same check over every collected page. The file holds page text, so it
     * exists only where ml/data/llm/final was built; elsewhere this test is a no-op.
     */
    @Test
    fun `matches every collected page when the local data is present`() {
        val file = File("../../ml/data/llm/final/llm_prompt_cases_full.jsonl")
        if (!file.exists()) return
        for (row in file.readLines().filter { it.isNotBlank() }.map(::JSONObject)) {
            val obs = observationFromJson(row.getJSONObject("observation"))
            assertEquals(row.getString("prompt"), builder.build(obs), obs.url)
        }
    }

    @Test
    fun `truncates by characters, not UTF-16 units`() {
        assertEquals("😀😀", PyText.take("😀😀😀", 2))
        assertEquals(3, PyText.length("😀😀😀"))
    }
}
