package com.guadian.ondevice.store

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Judge
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.util.UrlKey

interface RiskStore {
    fun find(url: String): Verdict?
    fun save(url: String, verdict: Verdict)
}

/**
 * Thread-safe LRU cache keyed by [UrlKey], so a verdict for one path is never
 * reused for another path on the same host.
 */
class InMemoryRiskStore(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : RiskStore {
    private val verdicts = object : LinkedHashMap<String, Verdict>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Verdict>): Boolean =
            size > maxEntries
    }

    override fun find(url: String): Verdict? = synchronized(verdicts) { verdicts[UrlKey.of(url)] }

    // UNKNOWN is never reused: a later attempt may have a better answer.
    override fun save(url: String, verdict: Verdict) {
        if (verdict.risk == Risk.UNKNOWN) return
        synchronized(verdicts) { verdicts[UrlKey.of(url)] = verdict }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 500
    }
}

/** Pipeline stage view of a [RiskStore]: a hit is CERTAIN, a miss passes on. */
class StoreJudge(private val store: RiskStore) : Judge {
    override suspend fun judge(url: String): Verdict {
        return store.find(url)?.copy(layer = Layer.STORE, confidence = Confidence.CERTAIN)
            ?: MISS
    }

    private companion object {
        val MISS = Verdict(
            risk = Risk.UNKNOWN,
            confidence = Confidence.UNCERTAIN,
            layer = Layer.STORE,
            reason = ReasonCode.CACHE_MISS,
        )
    }
}
