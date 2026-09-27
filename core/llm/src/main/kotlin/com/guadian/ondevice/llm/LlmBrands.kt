package com.guadian.ondevice.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brand data shared with Python training: `model/runtime/llm_brands.json`,
 * exported by model/llm/prompt_format.py. Lists keep the exported order,
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

    companion object {
        val default: LlmBrands by lazy {
            val stream = LlmBrands::class.java.getResourceAsStream("/llm_brands.json")
                ?: error("llm_brands.json resource is missing")
            LlmBrands(JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }))
        }

        private fun JSONArray.strings() = List(length()) { getString(it) }
        private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
        private fun JSONArray.arrays() = List(length()) { getJSONArray(it) }
    }
}
