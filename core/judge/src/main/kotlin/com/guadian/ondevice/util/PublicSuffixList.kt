package com.guadian.ondevice.util

import java.net.IDN

/**
 * Registrable-domain lookup ("eTLD+1") backed by Mozilla's Public Suffix
 * List, bundled as `model/runtime/public_suffix_list.dat` (MPL-2.0; version
 * recorded in docs/provenance.md). Includes the private section, so
 * `a.github.io` and `b.github.io` are different sites.
 */
class PublicSuffixList(lines: Sequence<String>) {
    private val rules = HashSet<String>()
    private val wildcards = HashSet<String>() // "*.ck" stored as "ck"
    private val exceptions = HashSet<String>() // "!www.ck" stored as "www.ck"

    init {
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            when {
                line.startsWith("!") -> exceptions += ascii(line.substring(1))
                line.startsWith("*.") -> wildcards += ascii(line.substring(2))
                else -> rules += ascii(line)
            }
        }
    }

    /**
     * `m.news.nate.com` -> `nate.com`, `m.kbstar.co.kr` -> `kbstar.co.kr`.
     * Returns null when [host] is itself a public suffix, and the host
     * unchanged for IP literals.
     */
    fun registrableDomain(host: String): String? {
        val normalized = host.lowercase().trim('.')
        if (normalized.isEmpty()) return null
        if (isIpLiteral(normalized)) return normalized
        val labels = normalized.split('.')
        val suffixLength = publicSuffixLength(labels)
        if (labels.size <= suffixLength) return null
        return labels.takeLast(suffixLength + 1).joinToString(".")
    }

    private fun publicSuffixLength(labels: List<String>): Int {
        // Exception rules win over every other rule.
        for (i in labels.indices) {
            if (labels.subList(i, labels.size).joinToString(".") in exceptions) return labels.size - i - 1
        }
        var best = 1 // the implicit "*" rule
        for (i in labels.indices) {
            val length = labels.size - i
            if (length <= best) break
            val candidate = labels.subList(i, labels.size).joinToString(".")
            val parent = labels.subList(i + 1, labels.size).joinToString(".")
            if (candidate in rules || (parent.isNotEmpty() && parent in wildcards)) best = length
        }
        return best
    }

    private fun isIpLiteral(host: String): Boolean =
        ':' in host || host.all { it.isDigit() || it == '.' }

    private fun ascii(rule: String): String =
        runCatching { IDN.toASCII(rule, IDN.ALLOW_UNASSIGNED) }.getOrDefault(rule).lowercase()

    companion object {
        val default: PublicSuffixList by lazy {
            val stream = PublicSuffixList::class.java.getResourceAsStream("/public_suffix_list.dat")
                ?: error("public_suffix_list.dat resource is missing")
            stream.bufferedReader().useLines { PublicSuffixList(it) }
        }
    }
}
