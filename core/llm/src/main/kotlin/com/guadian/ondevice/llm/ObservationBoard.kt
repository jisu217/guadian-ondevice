package com.guadian.ondevice.llm

/**
 * Hands a page observation from page inspection to the LLM stage.
 *
 * The pipeline passes only a URL between stages, so page inspection posts what
 * it saw here and the LLM stage takes it for the same URL. Entries are removed
 * when taken; the oldest are dropped past [capacity], so a judgment that ends
 * before the LLM stage (a certain page-content verdict) leaves nothing behind
 * for long.
 */
class ObservationBoard(private val capacity: Int = 16) {
    private val entries = object : LinkedHashMap<String, PageObservation>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageObservation>?) = size > capacity
    }

    @Synchronized
    fun post(url: String, observation: PageObservation) {
        entries.remove(url)
        entries[url] = observation
    }

    @Synchronized
    fun take(url: String): PageObservation? = entries.remove(url)
}
