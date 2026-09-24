package com.guardian.ondevice.model

data class UrlFeatures(
    val values: Map<String, Double>,
) {
    fun vector(): List<Double> {
        return UrlFeatureExtractor.FEATURE_NAMES.map { name -> values.getValue(name) }
    }
}

private data class ParsedUrl(val host: String, val path: String, val query: String)

class UrlFeatureExtractor {
    fun extract(url: String): UrlFeatures {
        val normalized = normalizeForParse(url)
        val parsed = parse(normalized)
        val host = parsed.host
        val path = parsed.path
        val query = parsed.query
        val searchable = listOf(host, path, query).joinToString(" ").lowercase()
        val tokens = tokenize(searchable)
        val queryPairs = parseQueryPairs(query)

        return UrlFeatures(
            mapOf(
                "url_length" to url.trim().length.toDouble(),
                "host_length" to host.length.toDouble(),
                "path_length" to path.length.toDouble(),
                "query_length" to query.length.toDouble(),
                "digit_ratio" to ratio(url) { it.isDigit() },
                "special_char_ratio" to ratio(url) { !it.isLetterOrDigit() },
                "subdomain_count" to subdomainCount(host).toDouble(),
                "has_apk" to hasApk(path, query, tokens),
                "has_login" to containsAny(tokens, LOGIN_TOKENS),
                "has_event" to containsAny(tokens, EVENT_TOKENS),
                "has_free" to containsAny(tokens, FREE_TOKENS),
                "has_shortener_token" to hasShortenerSignal(host, tokens),
                "brand_keyword_present" to containsAny(tokens, BRAND_KEYWORDS),
                "official_domain_match" to if (isOfficialDomain(host)) 1.0 else 0.0,
                "suspicious_tld" to if (lastLabel(host) in SUSPICIOUS_TLDS) 1.0 else 0.0,
                "query_param_count" to queryParamCount(queryPairs, query).toDouble(),
                "utm_param_count" to utmParamCount(queryPairs).toDouble(),
                "tracking_param_count" to trackingParamCount(queryPairs).toDouble(),
                "has_nested_url" to hasNestedUrl(queryPairs, query),
                "has_encoded_url" to hasEncodedUrl(query),
                "has_ad_click_host" to containsAny(tokens, AD_CLICK_HOST_TOKENS),
                "has_base64_like_token" to hasBase64LikeToken(tokens),
            )
        )
    }

    private fun normalizeForParse(url: String): String {
        val value = url.trim()
        return if ("://" in value) value else "https://$value"
    }

    // Custom parsing keeps IDN hosts aligned with Python's training extractor.
    private fun parse(normalized: String): ParsedUrl {
        val afterScheme = normalized.substringAfter("://", normalized)
        val authority = afterScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        val host = authority.substringAfterLast("@").substringBefore(":").lowercase()

        val afterAuthority = afterScheme.substring(minOf(authority.length, afterScheme.length))
        val pathAndQuery = afterAuthority.substringBefore("#")
        val path = pathAndQuery.substringBefore("?")
        val query = if ("?" in pathAndQuery) pathAndQuery.substringAfter("?") else ""

        return ParsedUrl(host = host, path = path, query = query)
    }

    private fun tokenize(value: String): Set<String> {
        return value
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun ratio(value: String, predicate: (Char) -> Boolean): Double {
        val stripped = value.trim()
        if (stripped.isEmpty()) return 0.0
        return stripped.count(predicate).toDouble() / stripped.length
    }

    private fun subdomainCount(host: String): Int {
        val labels = host.split(".").filter { it.isNotEmpty() }
        return maxOf(0, labels.size - 2)
    }

    private fun containsAny(tokens: Set<String>, candidates: Set<String>): Double {
        return if (tokens.any { it in candidates }) 1.0 else 0.0
    }

    private fun hasApk(path: String, query: String, tokens: Set<String>): Double {
        return if (
            path.lowercase().endsWith(".apk") ||
            tokens.any { it in APK_TOKENS } ||
            ".apk" in query.lowercase()
        ) {
            1.0
        } else {
            0.0
        }
    }

    private fun hasShortenerSignal(host: String, tokens: Set<String>): Double {
        return if (host in SHORTENER_HOSTS || tokens.any { it in SHORTENER_TOKENS }) 1.0 else 0.0
    }

    private fun lastLabel(host: String): String {
        return host.split(".").filter { it.isNotEmpty() }.lastOrNull() ?: ""
    }

    private fun parseQueryPairs(query: String): List<Pair<String, String>> {
        if (query.isEmpty()) return emptyList()
        return query
            .split(Regex("[&;]"))
            .filter { it.isNotEmpty() }
            .map { part ->
                val key = part.substringBefore("=").percentDecode().lowercase()
                val value = if ("=" in part) part.substringAfter("=").percentDecode() else ""
                key to value
            }
    }

    private fun queryParamCount(queryPairs: List<Pair<String, String>>, rawQuery: String): Int {
        if (queryPairs.isNotEmpty()) return queryPairs.size
        if (rawQuery.isEmpty()) return 0
        return rawQuery.split(Regex("[&;]")).count { it.isNotEmpty() }
    }

    private fun utmParamCount(queryPairs: List<Pair<String, String>>): Int {
        return queryPairs.count { (key, _) -> key.startsWith("utm_") }
    }

    private fun trackingParamCount(queryPairs: List<Pair<String, String>>): Int {
        return queryPairs.count { (key, _) -> key in TRACKING_PARAM_KEYS }
    }

    private fun hasNestedUrl(queryPairs: List<Pair<String, String>>, rawQuery: String): Double {
        for ((key, value) in queryPairs) {
            val loweredValue = value.lowercase()
            if (key in NESTED_URL_PARAM_KEYS && looksLikeUrl(loweredValue)) return 1.0
            if (looksLikeUrl(loweredValue)) return 1.0
        }

        return if (looksLikeUrl(rawQuery.percentDecode().lowercase())) 1.0 else 0.0
    }

    private fun hasEncodedUrl(query: String): Double {
        val lowered = query.lowercase()
        return if ("http%3a" in lowered || "https%3a" in lowered || "%2f%2f" in lowered) 1.0 else 0.0
    }

    private fun looksLikeUrl(value: String): Boolean {
        return "http://" in value || "https://" in value
    }

    private fun hasBase64LikeToken(tokens: Set<String>): Double {
        return if (tokens.any { BASE64_LIKE_PATTERN.matches(it) }) 1.0 else 0.0
    }

    private fun String.percentDecode(): String {
        return try {
            java.net.URLDecoder.decode(this, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            this
        }
    }

    companion object {
        val FEATURE_NAMES = listOf(
            "url_length",
            "host_length",
            "path_length",
            "query_length",
            "digit_ratio",
            "special_char_ratio",
            "subdomain_count",
            "has_apk",
            "has_login",
            "has_event",
            "has_free",
            "has_shortener_token",
            "brand_keyword_present",
            "official_domain_match",
            "suspicious_tld",
            "query_param_count",
            "utm_param_count",
            "tracking_param_count",
            "has_nested_url",
            "has_encoded_url",
            "has_ad_click_host",
            "has_base64_like_token",
        )

        private val APK_TOKENS = setOf("apk", "download", "install")
        private val LOGIN_TOKENS = setOf("login", "signin", "auth", "account", "verify", "verification")
        private val EVENT_TOKENS = setOf("event", "promo", "promotion", "reward", "coupon")
        private val FREE_TOKENS = setOf("free", "gift", "prize", "win", "winner")
        private val SHORTENER_HOSTS = setOf("bit.ly", "tinyurl.com", "t.co", "short.test")
        private val SHORTENER_TOKENS = setOf("short", "go", "r", "l")
        private val BRAND_KEYWORDS = setOf("bank", "kakao", "naver", "google", "samsung", "sk", "toss")
        private val SUSPICIOUS_TLDS = setOf("zip", "top", "xyz", "click", "test")
        private val TRACKING_PARAM_KEYS = setOf(
            "gclid",
            "dclid",
            "fbclid",
            "msclkid",
            "gad_source",
            "gad_campaignid",
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_content",
            "utm_term",
        )
        private val NESTED_URL_PARAM_KEYS = setOf("url", "u", "target", "redirect", "redirect_url", "landing_url", "dest", "destination")
        private val AD_CLICK_HOST_TOKENS = setOf(
            "ad",
            "ads",
            "adclick",
            "adservice",
            "adn",
            "adscope",
            "appier",
            "criteo",
            "dable",
            "doubleclick",
            "googlesyndication",
            "haimlabs",
            "mobon",
            "outbrain",
            "taboola",
            "widerplanet",
        )
        private val BASE64_LIKE_PATTERN = Regex("^[A-Za-z0-9_-]{32,}$")

        // Shared resource also read by Python training.
        private val OFFICIAL_DOMAINS: Set<String> by lazy { loadOfficialDomains() }

        private fun loadOfficialDomains(): Set<String> {
            val stream = UrlFeatureExtractor::class.java.getResourceAsStream("/official_domains.txt")
                ?: return emptySet()
            return stream.bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            }
        }

        private fun isOfficialDomain(host: String): Boolean {
            if (host.isEmpty()) return false
            return host in OFFICIAL_DOMAINS || OFFICIAL_DOMAINS.any { host.endsWith(".$it") }
        }
    }
}
