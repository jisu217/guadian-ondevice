package com.guadian.ondevice.llm

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks PageSignals against data/fixtures/llm_page_signal_cases.json, written from the original rules. */
class PageSignalsParityTest {
    private val cases = JSONObject(File("../../model/data/fixtures/llm_page_signal_cases.json").readText())

    @Test
    fun `form asks match the original rules`() {
        val forms = cases.getJSONArray("forms")
        for (i in 0 until forms.length()) {
            val case = forms.getJSONObject(i)
            val raw = case.getJSONArray("fields")
            val fields = List(raw.length()) { j ->
                val f = raw.getJSONObject(j)
                PageSignals.Field(
                    type = f.optString("type"),
                    name = f.optString("name").ifEmpty { null },
                    id = f.optString("id").ifEmpty { null },
                    placeholder = f.optString("placeholder").ifEmpty { null },
                    autocomplete = f.optString("autocomplete").ifEmpty { null },
                )
            }
            val expected = case.getJSONArray("asks").let { a -> List(a.length()) { a.getString(it) } }
            assertEquals(expected, PageSignals.formAsks(fields), raw.toString())
        }
    }

    @Test
    fun `external form actions match the original rules`() {
        val actions = cases.getJSONArray("actions")
        for (i in 0 until actions.length()) {
            val case = actions.getJSONObject(i)
            val action = if (case.isNull("action")) null else case.getString("action")
            assertEquals(case.getBoolean("external"), PageSignals.externalAction(case.getString("final_url"), action),
                case.toString())
        }
    }

    @Test
    fun `text collapses like the original`() {
        val texts = cases.getJSONArray("texts")
        for (i in 0 until texts.length()) {
            val case = texts.getJSONObject(i)
            assertEquals(case.getString("collapsed"), PageSignals.collapse(case.getString("raw")))
        }
    }
}
