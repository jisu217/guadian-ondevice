package com.guadian.ondevice.store

import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.url.UrlKey

/**
 * Verdicts already made, keyed by URL. Each is saved with the version of the verdict
 * table that made it, and found only under the same version: a verdict made by an
 * earlier table is judged again.
 */
interface VerdictStore {
    fun find(url: String, tableVersion: String): Verdict?
    fun save(url: String, tableVersion: String, verdict: Verdict)
}

/**
 * Thread-safe LRU cache keyed by [UrlKey], so a verdict for one path is never
 * reused for another path on the same host.
 */
class InMemoryVerdictStore(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : VerdictStore {
    private class Entry(val tableVersion: String, val verdict: Verdict)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    override fun find(url: String, tableVersion: String): Verdict? = synchronized(entries) {
        entries[UrlKey.of(url)]?.takeIf { it.tableVersion == tableVersion }?.verdict
    }

    // UNKNOWN is never reused: a later attempt may have a better answer.
    override fun save(url: String, tableVersion: String, verdict: Verdict) {
        if (verdict.risk == Risk.UNKNOWN) return
        synchronized(entries) { entries[UrlKey.of(url)] = Entry(tableVersion, verdict) }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
