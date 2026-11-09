package com.guadian.ondevice.llm

import com.guadian.ondevice.verdict.Evidence
import com.guadian.ondevice.verdict.VerdictTable.Facts
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UrlSignals and PageFacts must compute the facts contract/facts.py computes for the shared fixture. */
class FactsParityTest {
    private val cases = JSONObject(File("../../ml/fixtures/llm/facts_cases.json").readText())
    private val urlSignals = UrlSignals()
    private val pageFacts = PageFacts()

    @Test
    fun `url facts match`() {
        val urls = cases.getJSONArray("urls")
        assertTrue(urls.length() > 0)
        for (i in 0 until urls.length()) {
            val case = urls.getJSONObject(i)
            val url = case.getString("url")
            assertEquals(case.getJSONObject("facts").toWire(), urlSignals.inspect(url).facts.toWire(), url)
        }
    }

    @Test
    fun `page facts match`() {
        val pages = cases.getJSONArray("pages")
        assertTrue(pages.length() > 0)
        for (i in 0 until pages.length()) {
            val case = pages.getJSONObject(i)
            val obs = observationFromJson(case.getJSONObject("observation"))
            val actual = pageFacts.evidence(obs, urlSignals.inspect(obs.url)).facts
            assertEquals(case.getJSONObject("facts").toWire(), actual.toWire(), obs.toString())
        }
    }

    @Test
    fun `claim kinds match`() {
        val claims = cases.getJSONArray("claims")
        assertTrue(claims.length() > 0)
        val brands = LlmBrands.default
        for (i in 0 until claims.length()) {
            val case = claims.getJSONObject(i)
            val name = if (case.isNull("name")) null else case.getString("name")
            assertEquals(case.getString("kind"), brands.claimKind(name, case.getString("host")).wireName, name)
        }
    }

    /** The fixture's fields, as text, so a mismatch shows which one differs. */
    private fun JSONObject.toWire(): Map<String, String> = keys().asSequence().associateWith { if (isNull(it)) "null" else get(it).toString() }

    private fun Facts.toWire(): Map<String, String> = mapOf(
        "blocklisted" to blocklisted.toString(),
        "host_imitation" to hostImitation.wireName,
        "opened" to opened.toString(),
        "official" to official.toString(),
        "apk_link" to apkLink.toString(),
        "sensitive_field" to sensitiveField.toString(),
        "personal_field" to personalField.toString(),
        "claim" to (claim?.wireName ?: "null"),
        "sign_in" to signIn.toString(),
        "payment" to payment.toString(),
        "investment" to investment.toString(),
        "reward_pressure" to rewardPressure.toString(),
        "content_farm" to contentFarm.toString(),
    )
}
