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
 * - `MEDIUM` personal_info on a page that shows neither a personal field nor a form
 *   worded to collect personal information ([personalInfoUnsupported]) becomes `LOW`:
 *   the labeling guide (rule 5) calls a page personal_info only on that ground.
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
    /** Asks that make a form collect personal information; email alone does not. */
    private val PERSONAL_ASKS = setOf("name", "phone", "password", "rrn", "card", "account")
    /**
     * Words that show a shown form with unnamed fields collects contact details. Without such
     * a form they are only text, often a menu link, and are not ground.
     */
    private val CONTACT_WORDS = listOf("연락처", "휴대폰", "핸드폰", "전화번호")

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
        if (risk == Risk.MEDIUM && type == "personal_info" && personalInfoUnsupported(prompt)) {
            return verdict(Risk.LOW, Confidence.CERTAIN, ReasonCode.LLM_UNSUPPORTED_PERSONAL_INFO, RiskCategory.NONE,
                host, null)
        }
        val category = if (risk == Risk.LOW) RiskCategory.NONE else TYPES.getValue(type)
        return verdict(risk, Confidence.CERTAIN, ReasonCode.LLM_VERDICT, category, host, evidence.ifEmpty { null })
    }

    /**
     * The prompt gives no ground for a personal-information warning (answer.personal_info_unsupported):
     * no field asks for personal information, and no shown form with fields other than search
     * or email comes with page text naming a contact field or asking consent to collect personal information.
     */
    fun personalInfoUnsupported(prompt: String): Boolean {
        val lines = prompt.lines()
        fun line(key: String) = lines.firstOrNull { it.startsWith("$key: ") } ?: ""
        val forms = line("입력 칸").removePrefix("입력 칸: ").replace("(외부로 전송)", "")
        val asks = forms.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (asks.any { it in PERSONAL_ASKS }) return false
        return !formWordedForContact(asks, line("본문"))
    }

    /**
     * A shown form with fields other than search or email whose page text names a
     * contact field or asks consent to collect personal information (labeling guide,
     * rule 5). [kinds] are the form kinds as the prompt's 입력 칸 line lists them.
     */
    fun formWordedForContact(kinds: Set<String>, body: String): Boolean =
        (kinds - setOf("search", "email")).isNotEmpty() &&
            (CONTACT_WORDS.any { it in body } || ("개인정보" in body && ("수집" in body || "동의" in body)))

    /** The question model's answers (docs/design.md, section 5). */
    data class Answers(
        /** The organization the page claims to be; null when it claims none or the claim has no evidence. */
        val claims: String?,
        /** The prompt line that shows the claim, or empty. */
        val evidence: String,
        val signIn: Boolean,
        val payment: Boolean,
        val investment: Boolean,
        val rewardPressure: Boolean,
        val contentFarm: Boolean,
    )

    private val QUESTION_KEYS = listOf("sign_in", "payment", "investment", "reward_pressure", "content_farm")

    /** The reply grades the risk itself (run 3 and earlier) rather than answering the questions. */
    fun isGraded(reply: String?): Boolean = reply?.let(::firstJsonObject)?.has("risk") == true

    /**
     * The question model's answers (answer.read_answers), or null when the reply is unreadable:
     * every yes/no key must be a JSON boolean and `claims` a string or null. A claim whose
     * evidence is not one of the prompt's lines, verbatim, is dropped.
     */
    fun readAnswers(reply: String?, prompt: String): Answers? {
        val answer = reply?.let(::firstJsonObject) ?: return null
        if (QUESTION_KEYS.any { answer.opt(it) !is Boolean }) return null
        val rawClaims = if (answer.isNull("claims")) null else answer.opt("claims") as? String ?: return null
        val claims = rawClaims?.trim()?.ifEmpty { null }
        val evidence = answer.optString("evidence").trim()
        val grounded = claims != null && evidence.isNotEmpty() && prompt.lineSequence().any { it.trim() == evidence }
        return Answers(
            claims = if (grounded) claims else null,
            evidence = if (grounded) evidence else "",
            signIn = answer.getBoolean("sign_in"),
            payment = answer.getBoolean("payment"),
            investment = answer.getBoolean("investment"),
            rewardPressure = answer.getBoolean("reward_pressure"),
            contentFarm = answer.getBoolean("content_farm"),
        )
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
