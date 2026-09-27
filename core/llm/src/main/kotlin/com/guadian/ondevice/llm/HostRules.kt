package com.guadian.ondevice.llm

import com.guadian.ondevice.util.PublicSuffixList
import java.net.IDN

/**
 * Brand imitation judged from a host alone. Port of model/llm/host_rules.py:
 *
 * - HIGH: the host embeds a brand's official domain under another registrable
 *   domain (`roblox.com.mu`, `https-wvvw-roblox.com`)
 * - MEDIUM: the host pairs a brand token with an account word on a non-brand
 *   domain (`xfinityuserverification.weebly.com`)
 * - otherwise nothing: a brand name alone (`meritzevent.com`) is left to page content
 *
 * [HostVerdict.reason] text is part of the model's input and must stay
 * identical to the Python version.
 */
class HostRules(
    private val brands: LlmBrands = LlmBrands.default,
    private val suffixes: PublicSuffixList = PublicSuffixList.default,
) {
    data class HostVerdict(val risk: String, val type: String, val reason: String)

    /** The stronger verdict for the clicked URL or the landing page (shorteners hide the imitating host). */
    fun gradeCase(url: String, finalUrl: String?): HostVerdict? =
        listOfNotNull(url, finalUrl).distinct().mapNotNull(::gradeHost)
            .fold(null as HostVerdict?) { best, v -> if (best == null || rank(v) > rank(best)) v else best }

    fun gradeHost(url: String): HostVerdict? {
        val host = Text.hostname(url)?.removePrefix("www.")?.ifEmpty { null } ?: return null
        val asciiHost = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
        val registrable = suffixes.registrableDomain(asciiHost) ?: host
        val flat = host.replace("-", "")

        for ((_, officials) in brands.hostTokens) {
            if (officials.any { under(host, it) }) return null
            val embedded = officials.firstOrNull { embeds(host, flat, it) }
            if (embedded != null && officials.none { under(registrable, it) }) {
                return HostVerdict("HIGH", "impersonation", "host $host embeds $embedded but belongs to $registrable")
            }
        }

        val tokens = brands.hostTokens.map { it.first }.filter { it in flat }
        val token = tokens.maxByOrNull { it.length } ?: return null   // first of the longest, as Python's max
        val rest = flat.replace(token, "")
        val word = brands.accountWords.firstOrNull { it in rest } ?: return null
        return HostVerdict("MEDIUM", "impersonation", "host $host pairs brand '$token' with '$word' on $registrable")
    }

    private fun rank(v: HostVerdict) = if (v.risk == "HIGH") 2 else 1

    private fun under(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun embeds(host: String, flat: String, domain: String): Boolean {
        val pattern = Regex("(^|[.\\-])" + Regex.escape(domain) + "($|[.\\-/])")
        return pattern.containsMatchIn(host) || pattern.containsMatchIn(flat)
    }
}
