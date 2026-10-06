package com.guadian.ondevice.llm

import com.guadian.ondevice.verdict.VerdictTable
import org.json.JSONArray
import java.util.Locale
import org.json.JSONObject

/**
 * Brand data shared with Python training: `ml/runtime/llm_brands.json`,
 * exported by ml/guadian_ml/resources/llm_brands.py. Lists keep the exported order,
 * which decides ties.
 */
class LlmBrands(json: JSONObject) {
    class KoreanBrand(val name: String, val aliases: List<String>, val domains: List<String>)

    val korean: List<KoreanBrand> = json.getJSONArray("korean").objects().map {
        KoreanBrand(it.getString("name"), it.getJSONArray("aliases").strings(), it.getJSONArray("domains").strings())
    }
    val globalBrands: Map<String, List<String>> = json.getJSONArray("global_brands").arrays()
        .associateTo(LinkedHashMap()) { it.getString(0) to it.getJSONArray(1).strings() }
    val textMentions: List<Pair<String, String>> = json.getJSONArray("text_mentions").arrays()
        .map { it.getString(0) to it.getString(1) }
    val hostTokens: List<Pair<String, List<String>>> = json.getJSONArray("host_tokens").arrays()
        .map { it.getString(0) to it.getJSONArray(1).strings() }
    val accountWords: List<String> = json.getJSONArray("account_words").strings()
    /** Hosts a brand owns but anyone can publish on; a page there is not the brand speaking. */
    val userContentHosts: List<String> = json.getJSONArray("user_content_hosts").strings()

    /** The brand that owns [host]: the Korean table first, then the global brands (`Brands.official_brand_of`). */
    fun officialBrandOf(host: String): String? {
        val bare = host.removePrefix("www.")
        return korean.firstOrNull { brand -> brand.domains.any { covers(bare, it) } }?.name
            ?: globalBrands.entries.firstOrNull { (_, domains) -> domains.any { covers(bare, it) } }?.key
    }

    /**
     * The brand whose own site [host] is: owned by the table and not a platform where
     * anyone publishes, where a page is not the brand speaking (docs/design.md, section 4).
     */
    fun officialSiteOf(host: String): String? =
        officialBrandOf(host)?.takeUnless { userContentHosts.any { covers(host, it) } }

    /**
     * The verdict table's claim for the organization the model says the page claims to be
     * (facts.claim_kind): a brand whose name appears in [name], Korean table first, that
     * does or does not own [landingHost], or a brand the table does not list.
     */
    fun claimKind(name: String?, landingHost: String): VerdictTable.Claim {
        if (name.isNullOrEmpty()) return VerdictTable.Claim.NONE
        val squashed = PyText.squash(name)
        val host = landingHost.removePrefix("www.")
        fun owned(domains: List<String>) = if (domains.any { covers(host, it) }) VerdictTable.Claim.NONE else VerdictTable.Claim.LISTED_OTHER
        korean.firstOrNull { brand ->
            brand.aliases.any { val a = PyText.squash(it); PyText.length(a) >= MIN_ALIAS_CHARS && a in squashed }
        }?.let { return owned(it.domains) }
        val words = Regex("[a-z0-9]+").findAll(name.lowercase(Locale.ROOT)).map { it.value }.toSet()
        val token = textMentions.firstOrNull { (n, _) -> PyText.squash(n) in words || PyText.squash(n) == squashed }?.second
            ?: globalBrands.keys.firstOrNull { it in words || it == squashed }
        return token?.let { owned(globalBrands.getValue(it)) } ?: VerdictTable.Claim.UNLISTED
    }

    companion object {
        private const val MIN_ALIAS_CHARS = 3
        val default: LlmBrands by lazy {
            val stream = LlmBrands::class.java.getResourceAsStream("/llm_brands.json")
                ?: error("llm_brands.json resource is missing")
            LlmBrands(JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }))
        }

        private fun covers(host: String, domain: String) = host == domain || host.endsWith(".$domain")
        private fun JSONArray.strings() = List(length()) { getString(it) }
        private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
        private fun JSONArray.arrays() = List(length()) { getJSONArray(it) }
    }
}
