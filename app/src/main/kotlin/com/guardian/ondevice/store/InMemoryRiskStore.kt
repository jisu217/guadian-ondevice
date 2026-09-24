package com.guardian.ondevice.store

import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.util.UrlHost

interface RiskStore {
    fun find(url: String): Verdict?
    fun save(url: String, verdict: Verdict)
}

class InMemoryRiskStore : RiskStore {
    private val verdicts = linkedMapOf<String, Verdict>()

    override fun find(url: String): Verdict? {
        return verdicts[keyOf(url)]
    }

    override fun save(url: String, verdict: Verdict) {
        verdicts[keyOf(url)] = verdict
    }

    // Falls back to the trimmed, lowercased URL itself when host parsing
    // fails, so an unparsable input still gets a stable (if imperfect) cache
    // key instead of silently never being cached.
    private fun keyOf(url: String): String {
        return UrlHost.of(url) ?: url.trim().lowercase()
    }
}
