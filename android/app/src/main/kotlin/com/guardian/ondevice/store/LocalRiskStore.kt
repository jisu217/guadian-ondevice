package com.guardian.ondevice.store

import com.guardian.ondevice.judge.Verdict

class LocalRiskStore {
    private val verdicts = linkedMapOf<String, Verdict>()

    fun find(url: String): Verdict? {
        return verdicts[keyOf(url)]
    }

    fun save(url: String, verdict: Verdict) {
        verdicts[keyOf(url)] = verdict
    }

    private fun keyOf(url: String): String {
        val trimmed = url.trim()
        val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
        return runCatching { java.net.URI(candidate).host?.lowercase()?.removePrefix("www.") }
            .getOrNull()
            ?: trimmed.lowercase()
    }
}
