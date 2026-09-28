package com.guadian.ondevice.llm

import java.util.Locale

/**
 * Builds the LLM's user message from a [PageObservation]. Port of
 * `user_message` in ml/guadian_ml/llm/contract/prompt.py: the model was trained on that
 * output, so this must produce the same text character for character.
 * core/llm's parity test checks it against data/fixtures/llm_prompt_cases.jsonl.
 */
class PromptBuilder(
    private val brands: LlmBrands = LlmBrands.default,
    private val hostRules: HostRules = HostRules(brands),
    private val pageChars: Int = DEFAULT_PAGE_CHARS,
) {
    fun build(obs: PageObservation): String {
        val lines = mutableListOf("주소: ${PyText.take(obs.url, MAX_URL_CHARS)}")
        val finalUrl: String
        if (!obs.loaded) {
            lines += "페이지: 열리지 않음"
            finalUrl = obs.url
        } else {
            finalUrl = obs.finalUrl ?: obs.url
            val finalHost = PyText.asciiHost(finalUrl)
            if (finalHost.isNotEmpty() && finalHost != PyText.asciiHost(obs.url)) {
                lines += "도착: $finalHost (이동 ${obs.redirects}회)"
            }
            lines += "페이지: 상태 ${obs.status}, 본문 ${obs.textChars}자"
            if (obs.title.isNotEmpty()) lines += "제목: ${PyText.take(obs.title, MAX_TITLE_CHARS)}"
            if (obs.textChars < SPARSE_TEXT_CHARS || (obs.status ?: 0) >= 400) {
                val brand = officialBrandOf(finalHost)
                lines += if (brand != null) "공식 도메인: $finalHost = $brand" else "공식 도메인: $finalHost 표에 없음"
            }
            val named = mismatches(obs.title + " " + obs.text, finalHost)
            if (named.isNotEmpty()) {
                lines += "브랜드 언급: " + named.take(MAX_BRANDS).joinToString(", ") { (b, d) -> "$b(공식 $d)" } +
                    " / 실제 $finalHost"
            }
            if (obs.forms.isNotEmpty()) {
                val asks = obs.forms.flatMap { it.asks }.toSortedSet().joinToString(", ")
                val external = if (obs.forms.any { it.externalAction }) " (외부로 전송)" else ""
                lines += "입력 칸: ${asks.ifEmpty { "일반" }}$external"
            }
            obs.apkLinks.firstOrNull()?.let { lines += "APK 링크: ${PyText.take(it, MAX_APK_CHARS)}" }
        }
        hostRules.gradeCase(obs.url, if (obs.loaded) finalUrl else null)?.let { lines += "주소 흉내: ${it.reason}" }
        if (obs.loaded && obs.text.isNotEmpty()) lines += "본문: ${PyText.take(obs.text, pageChars)}"
        return lines.joinToString("\n")
    }

    /** The brand that owns [host]: the Korean table first, then the global brands. */
    private fun officialBrandOf(host: String): String? {
        val bare = host.removePrefix("www.")
        return brands.korean.firstOrNull { brand -> brand.domains.any { covers(bare, it) } }?.name
            ?: brands.globalBrands.entries.firstOrNull { (_, domains) -> domains.any { covers(bare, it) } }?.key
    }

    /** Brands named in [words] whose domains do not cover [host], Korean table first. */
    private fun mismatches(words: String, host: String): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        val squashed = PyText.squash(words)
        for (brand in brands.korean) {
            val named = brand.aliases.any { alias ->
                val a = PyText.squash(alias)
                PyText.length(a) >= MIN_ALIAS_CHARS && a in squashed
            }
            if (named && brand.domains.none { covers(host, it) }) found += brand.name to brand.domains.first()
        }
        val lowered = words.lowercase(Locale.ROOT)
        val seen = mutableSetOf<String>()
        for ((name, token) in brands.textMentions) {
            val domains = brands.globalBrands.getValue(token)
            if (token in seen || domains.any { covers(host, it) }) continue
            if (Regex("(?<![a-z])" + Regex.escape(name) + "(?![a-z])").containsMatchIn(lowered)) {
                seen += token
                found += token to domains.first()
            }
        }
        return found
    }

    private fun covers(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    companion object {
        const val DEFAULT_PAGE_CHARS = 500
        private const val MAX_URL_CHARS = 200
        private const val MAX_TITLE_CHARS = 80
        private const val MAX_APK_CHARS = 100
        private const val MAX_BRANDS = 4
        private const val SPARSE_TEXT_CHARS = 200
        private const val MIN_ALIAS_CHARS = 3
    }
}
