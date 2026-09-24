package com.guardian.ondevice.rules

class OfficialDomains {
    private val domainsByBrand = mapOf(
        "coupang" to setOf("coupang.com", "coupangeats.com"),
        "naver" to setOf("naver.com", "navercorp.com", "pay.naver.com"),
        "kakao" to setOf("kakao.com", "kakaopay.com", "kakaocorp.com"),
        "kb" to setOf("kbstar.com", "kbcard.com", "kbfg.com"),
        "shinhan" to setOf("shinhan.com", "shinhancard.com"),
    )

    // Matching on the raw brand key (e.g. "kb") would false-positive on unrelated hosts
    // like "bankbook.com". Registrable labels (kbstar, kbcard, kbfg, ...) are long enough
    // to be a meaningful signal on their own.
    private val mentionLabelsByBrand: Map<String, Set<String>> = domainsByBrand.mapValues { (_, domains) ->
        domains.map { registrableLabel(it) }.toSet()
    }

    fun brandForOfficialHost(host: String): String? {
        val normalized = host.lowercase()
        return domainsByBrand.entries.firstOrNull { (_, domains) ->
            domains.any { normalized == it || normalized.endsWith(".$it") }
        }?.key
    }

    fun brandMentionedInHost(host: String): String? = firstMentionedBrand(host)

    // Same label set as brandMentionedInHost, applied to rendered page text
    // (title + body) instead of the host string. Lets PageContentJudge catch a
    // fake login page that names a brand in its content even when the URL
    // itself carries no brand token.
    fun brandMentionedInText(text: String): String? = firstMentionedBrand(text)

    private fun firstMentionedBrand(text: String): String? {
        val normalized = text.lowercase()
        return mentionLabelsByBrand.entries.firstOrNull { (_, labels) ->
            labels.any { normalized.contains(it) }
        }?.key
    }

    private fun registrableLabel(domain: String): String {
        val parts = domain.split(".")
        return if (parts.size >= 2) parts[parts.size - 2] else domain
    }
}
