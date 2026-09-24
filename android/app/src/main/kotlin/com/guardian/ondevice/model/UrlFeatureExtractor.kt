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
            )
        )
    }

    private fun normalizeForParse(url: String): String {
        val value = url.trim()
        return if ("://" in value) value else "https://$value"
    }

    // Deliberately avoids java.net.URI: its single-String constructor returns a
    // null host for internationalized domains (e.g. "https://알뜰상조.com/") instead
    // of throwing, which would silently zero out every host-based feature. This
    // mirrors Python's urllib.parse.urlparse (used for training in
    // url_features.py) closely enough that the same URL yields the same
    // features on both sides — the property that actually matters here.
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
        )

        private val APK_TOKENS = setOf("apk", "download", "install")
        private val LOGIN_TOKENS = setOf("login", "signin", "auth", "account", "verify", "verification")
        private val EVENT_TOKENS = setOf("event", "promo", "promotion", "reward", "coupon")
        private val FREE_TOKENS = setOf("free", "gift", "prize", "win", "winner")
        private val SHORTENER_HOSTS = setOf("bit.ly", "tinyurl.com", "t.co", "short.test")
        private val SHORTENER_TOKENS = setOf("short", "go", "r", "l")
        private val BRAND_KEYWORDS = setOf("bank", "kakao", "naver", "google", "samsung", "sk", "toss")
        private val SUSPICIOUS_TLDS = setOf("zip", "top", "xyz", "click", "test")

        // Loaded once from model/data/official_domains.txt (Tranco top 50k +
        // known brand domains), bundled as a classpath resource via the
        // `resources.srcDir("model/data")` entry in build.gradle.kts. Python's
        // url_features.py reads the same file directly, so there is exactly
        // one allowlist instead of two hand-synced copies.
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
