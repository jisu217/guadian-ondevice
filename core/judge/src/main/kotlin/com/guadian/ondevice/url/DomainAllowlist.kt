package com.guadian.ondevice.url

/**
 * The ~50k-domain allowlist in `ml/runtime/official_domains.txt`, built by
 * `python -m guadian_ml.resources.official_domains`.
 */
class DomainAllowlist(domains: Set<String>) {
    private val domains: Set<String> = domains.mapTo(HashSet()) { it.trim().lowercase() }

    /**
     * True when [host] or any parent domain is listed. Walks the host's own
     * suffixes (a handful of set lookups) instead of scanning every listed
     * domain.
     */
    fun coversHost(host: String): Boolean {
        if (host.isEmpty()) return false
        return generateSequence(host) { it.substringAfter('.', "").ifEmpty { null } }
            .any { it in domains }
    }

    fun containsExactly(domain: String): Boolean = domain in domains

    companion object {
        val default: DomainAllowlist by lazy {
            val stream = DomainAllowlist::class.java.getResourceAsStream("/official_domains.txt")
                ?: error("official_domains.txt resource is missing")
            DomainAllowlist(
                stream.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.toSet() }
            )
        }
    }
}
