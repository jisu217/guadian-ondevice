package com.guadian.ondevice.llm

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The model's input must be rendered exactly as the training tokenizer rendered it. */
class ChatFormatParityTest {
    @Test
    fun `matches the training chat template`() {
        val cases = JSONObject(File("../../ml/fixtures/llm/chat_cases.json").readText())
        assertEquals(cases.getString("system"), ChatFormat.SYSTEM)
        val rendered = cases.getJSONArray("rendered")
        for (i in 0 until rendered.length()) {
            val case = rendered.getJSONObject(i)
            assertEquals(case.getString("text"), ChatFormat.render(case.getString("user")))
        }
    }
}
