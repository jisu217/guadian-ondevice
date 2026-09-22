package com.guardian.ondevice.rules

class OfficialDomains {
    private val domainsByBrand = mapOf(
        "coupang" to setOf("coupang.com", "coupangeats.com"),
        "naver" to setOf("naver.com", "navercorp.com", "pay.naver.com"),
        "kakao" to setOf("kakao.com", "kakaopay.com", "kakaocorp.com"),
        "kb" to setOf("kbstar.com", "kbcard.com", "kbfg.com"),
        "shinhan" to setOf("shinhan.com", "shinhancard.com"),
    )

    fun brandForOfficialHost(host: String): String? {
        return domainsByBrand.entries.firstOrNull { (_, domains) ->
            domains.any { host == it || host.endsWith(".$it") }
        }?.key
    }

    fun brandMentionedInHost(host: String): String? {
        return domainsByBrand.keys.firstOrNull { brand ->
            host.contains(brand)
        }
    }
}
