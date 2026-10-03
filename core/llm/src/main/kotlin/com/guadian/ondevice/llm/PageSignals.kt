package com.guadian.ondevice.llm

import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Turns raw DOM readings into [PageObservation] fields with the rules the
 * training data was collected with (judge_agent.page_signals and _text_of in
 * ml/guadian_ml/llm/vendor). Quirks are kept on purpose: a field named "hotel" counts
 * as a phone field there, so it does here. [formAsks] adds field kinds the
 * original did not report (email, name, more phone wordings, search), defined in
 * prompt.field_kinds and read from the first 12 fields with 30-character labels,
 * as the collector stored them. Parity cases:
 * data/fixtures/llm_page_signal_cases.json.
 */
object PageSignals {
    /** A form control as read from the DOM; [type] defaults to the tag name. */
    data class Field(
        val type: String,
        val name: String? = null,
        val id: String? = null,
        val placeholder: String? = null,
        val autocomplete: String? = null,
        /** The user can see the field now (page_inspection.js); collected data without this reads as shown. */
        val shown: Boolean = true,
    )

    /** ml/runtime/page_inspection.js, which the collector runs as well. */
    val inspectionScript: String by lazy {
        val stream = PageSignals::class.java.getResourceAsStream("/page_inspection.js")
            ?: error("page_inspection.js resource is missing")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /*
     * The original patterns, with Python's Unicode-aware \s and \b spelled out so they
     * behave the same on the JVM and on Android (see PyText.PY_WHITESPACE).
     */
    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    private const val S = PyText.PY_WHITESPACE
    private const val START = "(?<!${PyText.PY_WORD})"
    private const val END = "(?!${PyText.PY_WORD})"
    private val RRN = Pattern.compile("주민$S*(등록)?$S*번호|주민번호|${START}rrn$END|resident", FLAGS)
    private val CARD = Pattern.compile("카드$S*번호|card$S*(number|no)|cvc|cvv", FLAGS)
    private val PHONE = Pattern.compile("전화|휴대폰|연락처|phone|tel$END", FLAGS)
    private val ACCOUNT = Pattern.compile("계좌$S*번호|account$S*(number|no)", FLAGS)
    /*
     * Kinds added after the original collector (prompt.field_kinds), read from the first
     * 12 fields with labels cut to 30 characters, as the collector stored them.
     * ASCII-only case folding, as Python's re.I | re.A.
     */
    private val EMAIL = Pattern.compile("e-?mail|이메일", Pattern.CASE_INSENSITIVE)
    private val NAME = Pattern.compile("(?<![a-z])name(?![a-z])|firstname|lastname|fullname|이름|성명", Pattern.CASE_INSENSITIVE)
    private val MOBILE = Pattern.compile("mobile|핸드폰", Pattern.CASE_INSENSITIVE)
    private val SEARCH = Pattern.compile("search|keyword|query|검색|(?<![a-z])q(?![a-z])", Pattern.CASE_INSENSITIVE)
    private val WHITESPACE = Pattern.compile("$S+")
    private val HOST = Regex("https?://([^/?#]+)")
    private val TWO_LEVEL = setOf("co", "or", "ne", "go", "ac", "re", "pe")

    const val MAX_FORMS = 6
    const val MAX_FIELDS = 20
    const val FIELD_KINDS_FIELDS = 12
    const val FIELD_KINDS_LABEL_CHARS = 30
    const val MAX_TEXT_CHARS = 2_500
    const val MAX_TITLE_CHARS = 200
    const val MAX_APK_LINKS = 5
    const val MAX_APK_CHARS = 120

    fun formAsks(fields: List<Field>): List<String> {
        val flags = sortedSetOf<String>()
        for (field in fields.take(MAX_FIELDS)) {
            val type = field.type.lowercase()
            val label = labelOf(field)
            if (type == "password") flags += "password"
            if (RRN.matcher(label).find()) flags += "rrn"
            if (CARD.matcher(label).find()) flags += "card"
            if (PHONE.matcher(label).find() || type == "tel") flags += "phone"
            if (ACCOUNT.matcher(label).find()) flags += "account"
        }
        for (field in fields.take(FIELD_KINDS_FIELDS)) {
            val type = field.type.lowercase()
            val label = PyText.take(labelOf(field), FIELD_KINDS_LABEL_CHARS)
            if (type == "email" || EMAIL.matcher(label).find()) flags += "email"
            if (NAME.matcher(label).find()) flags += "name"
            if (MOBILE.matcher(label).find()) flags += "phone"
            if (type == "search" || SEARCH.matcher(label).find()) flags += "search"
        }
        return flags.toList()
    }

    private fun labelOf(field: Field): String =
        listOfNotNull(field.name, field.id, field.placeholder, field.autocomplete)
            .filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * The LLM's observation from a page_inspection.js result, as the collector
     * reads it too (prompt.observation_from_inspection).
     */
    fun observe(url: String, finalUrl: String, status: Int, redirects: Int, json: JSONObject): PageObservation {
        val text = collapse(json.optString("text"))
        val forms = json.optJSONArray("forms").objects().take(MAX_FORMS).mapNotNull { form ->
            val fields = form.optJSONArray("fields").objects().map {
                Field(
                    type = it.optString("type"),
                    name = it.stringOrNull("name"),
                    id = it.stringOrNull("id"),
                    placeholder = it.stringOrNull("placeholder"),
                    autocomplete = it.stringOrNull("autocomplete"),
                    shown = it.optBoolean("shown", true),
                )
            }
            formObservation(fields, finalUrl, form.stringOrNull("action"))
        }
        // Anchor hrefs first, then URLs anywhere in the markup, as the collector gathered them.
        val apkLinks = LinkedHashSet<String>()
        (json.optJSONArray("apkAnchors").strings() + json.optJSONArray("apkInHtml").strings())
            .forEach { apkLinks += PyText.take(it, MAX_APK_CHARS) }
        return PageObservation(
            url = url,
            loaded = true,
            finalUrl = finalUrl,
            status = status,
            redirects = redirects,
            title = PyText.take(json.optString("title").trim(), MAX_TITLE_CHARS),
            text = PyText.take(text, MAX_TEXT_CHARS),
            textChars = PyText.length(text),
            forms = forms,
            apkLinks = apkLinks.take(MAX_APK_LINKS),
        )
    }

    /**
     * A form as the prompt sees it: only the fields the user can see now. A form
     * with no shown field (a closed popup, a hidden tracking form) is not on the page.
     */
    fun formObservation(fields: List<Field>, finalUrl: String, resolvedAction: String?): FormObservation? {
        val shown = fields.filter { it.shown }
        if (shown.isEmpty()) return null
        return FormObservation(formAsks(shown), externalAction(finalUrl, resolvedAction))
    }

    /** True when the form posts to another site; [resolvedAction] is null when the form has no action. */
    fun externalAction(finalUrl: String, resolvedAction: String?): Boolean {
        val host = hostOf(finalUrl) ?: ""
        val mySite = if (host.isNotEmpty()) siteOf(host) else null
        val actionHost = if (resolvedAction != null) hostOf(resolvedAction) else host
        return !actionHost.isNullOrEmpty() && mySite != null && siteOf(actionHost) != mySite
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

    /** Runs of whitespace to one space, trimmed, as judge_agent._text_of does. */
    fun collapse(raw: String): String = WHITESPACE.matcher(raw).replaceAll(" ").trim()

    // judge_agent.host_of: the authority of an http(s) URL, lowercased, port dropped.
    private fun hostOf(url: String): String? =
        HOST.find(url.lowercase())?.groupValues?.get(1)?.substringBefore(':')?.trim('.')

    // judge_agent.site_of: registrable domain by a co.kr-style heuristic, not the Public Suffix List.
    private fun siteOf(host: String): String {
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val take = if (parts.last().length <= 3 && parts[parts.size - 2] in TWO_LEVEL) 3 else 2
        return parts.takeLast(take).joinToString(".")
    }
}
