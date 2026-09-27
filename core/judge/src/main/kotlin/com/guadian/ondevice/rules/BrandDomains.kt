package com.guadian.ondevice.rules

import com.guadian.ondevice.url.PublicSuffixList

/**
 * Brand -> official registrable domains, loaded from
 * `ml/runtime/brand_domains.tsv` (also read by guadian_ml/resources/official_domains.py).
 */
class BrandDomains(
    private val domainsByBrand: Map<String, Set<String>> = defaultBrandDomains,
    private val suffixes: PublicSuffixList = PublicSuffixList.default,
) {
    // The registrable label of each official domain ("kbstar" for kbstar.com,
    // "naver" for naver.me). The raw brand key ("kb") is never matched on its
    // own: it would false-positive on hosts like "bankbook.com".
    private val labelsByBrand: Map<String, Set<String>> = domainsByBrand.mapValues { (_, domains) ->
        domains.mapNotNullTo(HashSet()) { domain ->
            suffixes.registrableDomain(domain)?.substringBefore('.')
        }
    }

    private val textPatterns: Map<String, Regex> = labelsByBrand.mapValues { (_, labels) ->
        Regex(labels.joinToString("|", prefix = "(?<![a-z0-9])(?:", postfix = ")(?![a-z0-9])") { Regex.escape(it) })
    }

    fun brandForOfficialHost(host: String): String? {
        val normalized = host.lowercase()
        return domainsByBrand.entries.firstOrNull { (_, domains) ->
            domains.any { normalized == it || normalized.endsWith(".$it") }
        }?.key
    }

    /** A host token (split on '.' and '-') equal to a brand label: "naver-login.xyz". */
    fun brandTokenInHost(host: String): String? {
        val tokens = host.lowercase().split('.', '-').toSet()
        return labelsByBrand.entries.firstOrNull { (_, labels) -> labels.any { it in tokens } }?.key
    }

    /** A brand label inside a longer host token: "kakaobankx.com". Weaker than a token match. */
    fun brandSubstringInHost(host: String): String? {
        val normalized = host.lowercase()
        return labelsByBrand.entries.firstOrNull { (_, labels) -> labels.any { it in normalized } }?.key
    }

    /** A brand label as a whole word in rendered page text (title + body). */
    fun brandMentionedInText(text: String): String? {
        val normalized = text.lowercase()
        return textPatterns.entries.firstOrNull { (_, pattern) -> pattern.containsMatchIn(normalized) }?.key
    }

    companion object {
        val defaultBrandDomains: Map<String, Set<String>> by lazy {
            val stream = BrandDomains::class.java.getResourceAsStream("/brand_domains.tsv")
                ?: error("brand_domains.tsv resource is missing")
            stream.bufferedReader().useLines { parseBrandDomains(it) }
        }

        fun parseBrandDomains(lines: Sequence<String>): Map<String, Set<String>> {
            val result = linkedMapOf<String, MutableSet<String>>()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val (brand, domain) = line.split('\t').map { it.trim().lowercase() }
                result.getOrPut(brand) { linkedSetOf() } += domain
            }
            return result
        }
    }
}
