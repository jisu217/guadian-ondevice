package com.guadian.ondevice.verdict

import com.guadian.ondevice.verdict.VerdictTable.Claim
import com.guadian.ondevice.verdict.VerdictTable.HostImitation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** VerdictTable must decide every case in the shared fixture as contract/verdict_table.py does. */
class VerdictTableParityTest {
    @Test
    fun `matches the shared verdict fixture`() {
        val cases = JSONArray(File("../../ml/fixtures/llm/verdict_cases.json").readText())
        assertTrue(cases.length() > 0)
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val facts = case.getJSONObject("facts")
            val expected = case.getJSONObject("decision")
            val actual = VerdictTable.default.decide(facts.toFacts())
            assertEquals(
                VerdictTable.Decision(expected.getString("risk"), expected.getString("type"),
                    expected.getString("reason"), expected.getBoolean("final")),
                actual, facts.toString(),
            )
        }
    }

    private fun JSONObject.toFacts() = VerdictTable.Facts(
        blocklisted = getBoolean("blocklisted"),
        hostImitation = HostImitation.entries.first { it.wireName == getString("host_imitation") },
        opened = bool("opened"),
        official = bool("official"),
        apkLink = bool("apk_link"),
        sensitiveField = bool("sensitive_field"),
        personalField = bool("personal_field"),
        claim = if (isNull("claim")) null else Claim.entries.first { it.wireName == getString("claim") },
        signIn = bool("sign_in"),
        payment = bool("payment"),
        investment = bool("investment"),
        rewardPressure = bool("reward_pressure"),
        contentFarm = bool("content_farm"),
    )

    private fun JSONObject.bool(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)
}
