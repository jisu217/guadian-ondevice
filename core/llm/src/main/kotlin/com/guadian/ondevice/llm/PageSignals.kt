package com.guadian.ondevice.llm

import java.util.regex.Pattern

/**
 * Turns raw DOM readings into [PageObservation] fields with the rules the
 * training data was collected with (judge_agent.page_signals and _text_of in
 * model/llm/vendor). Quirks are kept on purpose: a field named "hotel" counts
 * as a phone field there, so it does here. Parity cases:
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
    )

    // The original patterns, with Python's Unicode-aware \s and \b spelled out so they
    // behave the same on the JVM and on Android (see Text.PY_WHITESPACE).
    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    private const val S = Text.PY_WHITESPACE
    private const val START = "(?<!${Text.PY_WORD})"
    private const val END = "(?!${Text.PY_WORD})"
    private val RRN = Pattern.compile("주민$S*(등록)?$S*번호|주민번호|${START}rrn$END|resident", FLAGS)
    private val CARD = Pattern.compile("카드$S*번호|card$S*(number|no)|cvc|cvv", FLAGS)
    private val PHONE = Pattern.compile("전화|휴대폰|연락처|phone|tel$END", FLAGS)
    private val ACCOUNT = Pattern.compile("계좌$S*번호|account$S*(number|no)", FLAGS)
    private val WHITESPACE = Pattern.compile("$S+")
    private val HOST = Regex("https?://([^/?#]+)")
    private val TWO_LEVEL = setOf("co", "or", "ne", "go", "ac", "re", "pe")

    const val MAX_FORMS = 6
    const val MAX_FIELDS = 20
    const val MAX_TEXT_CHARS = 2_500
    const val MAX_TITLE_CHARS = 200
    const val MAX_APK_LINKS = 5
    const val MAX_APK_CHARS = 120

    fun formAsks(fields: List<Field>): List<String> {
        val flags = sortedSetOf<String>()
        for (field in fields.take(MAX_FIELDS)) {
            val type = field.type.lowercase()
            val label = listOfNotNull(field.name, field.id, field.placeholder, field.autocomplete)
                .filter { it.isNotEmpty() }.joinToString(" ")
            if (type == "password") flags += "password"
            if (RRN.matcher(label).find()) flags += "rrn"
            if (CARD.matcher(label).find()) flags += "card"
            if (PHONE.matcher(label).find() || type == "tel") flags += "phone"
            if (ACCOUNT.matcher(label).find()) flags += "account"
        }
        return flags.toList()
    }

    /** True when the form posts to another site; [resolvedAction] is null when the form has no action. */
    fun externalAction(finalUrl: String, resolvedAction: String?): Boolean {
        val host = hostOf(finalUrl) ?: ""
        val mySite = if (host.isNotEmpty()) siteOf(host) else null
        val actionHost = if (resolvedAction != null) hostOf(resolvedAction) else host
        return !actionHost.isNullOrEmpty() && mySite != null && siteOf(actionHost) != mySite
    }

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
