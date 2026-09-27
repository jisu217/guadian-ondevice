package com.guadian.ondevice.llm

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The prompt the app builds must equal the one the model was trained on.
 * data/fixtures/llm_prompt_cases.jsonl is written by model/llm/build_prompts.py.
 */
class PromptBuilderParityTest {
    private val builder = PromptBuilder()

    @Test
    fun `matches the python prompt fixture`() {
        val rows = File("../../model/data/fixtures/llm_prompt_cases.jsonl").readLines().filter { it.isNotBlank() }
        assertTrue(rows.size >= 10)
        for (row in rows.map(::JSONObject)) {
            val obs = observation(row.getJSONObject("observation"))
            assertEquals(row.getString("prompt"), builder.build(obs), obs.url)
        }
    }

    /**
     * The same check over every collected page. The file holds page text, so it
     * exists only where model/llm/data was built; elsewhere this test is a no-op.
     */
    @Test
    fun `matches every collected page when the local data is present`() {
        val file = File("../../model/llm/data/llm_prompt_cases_full.jsonl")
        if (!file.exists()) return
        for (row in file.readLines().filter { it.isNotBlank() }.map(::JSONObject)) {
            val obs = observation(row.getJSONObject("observation"))
            assertEquals(row.getString("prompt"), builder.build(obs), obs.url)
        }
    }

    @Test
    fun `truncates by characters, not UTF-16 units`() {
        assertEquals("😀😀", Text.take("😀😀😀", 2))
        assertEquals(3, Text.length("😀😀😀"))
    }

    private fun observation(o: JSONObject) = PageObservation(
        url = o.getString("url"),
        loaded = o.getBoolean("loaded"),
        finalUrl = o.optString("final_url").takeUnless { o.isNull("final_url") },
        status = if (o.isNull("status")) null else o.getInt("status"),
        redirects = o.getInt("redirects"),
        title = o.getString("title"),
        text = o.getString("text"),
        textChars = o.getInt("text_chars"),
        forms = o.getJSONArray("forms").let { forms ->
            List(forms.length()) { i ->
                val f = forms.getJSONObject(i)
                val asks = f.getJSONArray("asks")
                FormObservation(List(asks.length()) { asks.getString(it) }, f.getBoolean("external_action"))
            }
        },
        apkLinks = o.getJSONArray("apk_links").let { links -> List(links.length()) { links.getString(it) } },
    )
}
