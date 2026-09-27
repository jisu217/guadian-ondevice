package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Turns the model's reply into a [Verdict], trusting it only as far as it can be checked.
 *
 * - An unreadable reply (no JSON object, a risk or type outside the schema) is an
 *   uncertain `MEDIUM`: the user is told to check before continuing, and the result
 *   is not cached.
 * - `HIGH` needs one of the three types the criteria define for it (impersonation,
 *   credentials, apk) and evidence that is one of the prompt's own lines, verbatim.
 *   Otherwise it becomes `MEDIUM`. This is the original server's `_ground` check;
 *   training taught the model to quote a line, so a missing quote means the model
 *   is guessing.
 */
object AnswerReader {
    private val TYPES = mapOf(
        "impersonation" to RiskCategory.BRAND_IMPERSONATION,
        "credentials" to RiskCategory.CREDENTIALS,
        "apk" to RiskCategory.APK,
        "personal_info" to RiskCategory.PERSONAL_INFO,
        "payment" to RiskCategory.PAYMENT,
        "urgency" to RiskCategory.URGENCY,
        "investment" to RiskCategory.INVESTMENT,
        "contentfarm" to RiskCategory.CONTENT_FARM,
        "unverifiable" to RiskCategory.UNVERIFIABLE,
        "none" to RiskCategory.NONE,
    )
    private val HIGH_TYPES = setOf("impersonation", "credentials", "apk")

    fun read(reply: String?, prompt: String, host: String? = null): Verdict {
        val answer = reply?.let(::firstJsonObject) ?: return malformed(host)
        val risk = runCatching { Risk.valueOf(answer.optString("risk")) }.getOrNull()
            ?.takeIf { it != Risk.UNKNOWN } ?: return malformed(host)
        val type = answer.optString("type").takeIf { it in TYPES } ?: return malformed(host)
        val evidence = answer.optString("evidence").trim()

        if (risk == Risk.HIGH) {
            val grounded = evidence.isNotEmpty() && prompt.lineSequence().any { it.trim() == evidence }
            if (type !in HIGH_TYPES || !grounded) {
                return verdict(Risk.MEDIUM, Confidence.CERTAIN, ReasonCode.LLM_UNSUPPORTED_HIGH, TYPES.getValue(type),
                    host, evidence.ifEmpty { null })
            }
        }
        val category = if (risk == Risk.LOW) RiskCategory.NONE else TYPES.getValue(type)
        return verdict(risk, Confidence.CERTAIN, ReasonCode.LLM_VERDICT, category, host, evidence.ifEmpty { null })
    }

    /** The first JSON object in [text], read with a real parser so braces inside strings are handled. */
    private fun firstJsonObject(text: String): JSONObject? {
        var start = text.indexOf('{')
        while (start >= 0) {
            val parsed = runCatching { JSONTokener(text.substring(start)).nextValue() as? JSONObject }.getOrNull()
            if (parsed != null) return parsed
            start = text.indexOf('{', start + 1)
        }
        return null
    }

    private fun malformed(host: String?) =
        verdict(Risk.MEDIUM, Confidence.UNCERTAIN, ReasonCode.LLM_MALFORMED_OUTPUT, RiskCategory.UNVERIFIABLE, host, null)

    private fun verdict(risk: Risk, confidence: Confidence, reason: ReasonCode, category: RiskCategory,
                        host: String?, detail: String?) =
        Verdict(risk = risk, confidence = confidence, layer = Layer.LLM, reason = reason, category = category,
            host = host, detail = detail)
}
