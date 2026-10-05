package com.guadian.ondevice.llm

import org.json.JSONArray
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
        officialBrandOf(host)?.takeUnless { USER_CONTENT_HOSTS.any { covers(host, it) } }

    companion object {
        /** Kept identical to `USER_CONTENT_HOSTS` in ml/guadian_ml/llm/dataset/final_labels.py. */
        val USER_CONTENT_HOSTS = listOf(
            "blog.naver.com", "cafe.naver.com", "post.naver.com", "pf.kakao.com", "open.kakao.com",
            "story.kakao.com", "sites.google.com", "docs.google.com", "drive.google.com",
            "facebook.com", "instagram.com", "youtube.com",
        )

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
