package com.guadian.ondevice.store

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryVerdictStoreTest {
    private val verdict = Verdict(
        risk = Risk.LOW,
        confidence = Confidence.CERTAIN,
        layer = Layer.URL,
        reason = ReasonCode.OFFICIAL_SITE,
    )
    private val v1 = "table-v1"

    @Test
    fun `keys by normalized url, not by host`() {
        val store = InMemoryVerdictStore()

        store.save("https://www.Example.com/path?utm_source=ad#frag", v1, verdict)

        assertEquals(verdict, store.find("example.com/path", v1))
        assertNull(store.find("https://example.com/other", v1))
    }

    @Test
    fun `a verdict made by another table version is not found`() {
        val store = InMemoryVerdictStore()

        store.save("https://example.com/", v1, verdict)

        assertNull(store.find("https://example.com/", "table-v2"))
    }

    @Test
    fun `evicts the least recently used entry beyond capacity`() {
        val store = InMemoryVerdictStore(maxEntries = 2)
        store.save("https://a.example/", v1, verdict)
        store.save("https://b.example/", v1, verdict)
        store.find("https://a.example/", v1)

        store.save("https://c.example/", v1, verdict)

        assertEquals(verdict, store.find("https://a.example/", v1))
        assertNull(store.find("https://b.example/", v1))
    }

    @Test
    fun `ignores unknown verdicts`() {
        val store = InMemoryVerdictStore()

        store.save("https://example.com/", v1, verdict.copy(risk = Risk.UNKNOWN))

        assertNull(store.find("https://example.com/", v1))
    }

    @Test
    fun `survives concurrent writers`() = runBlocking {
        val store = InMemoryVerdictStore(maxEntries = 10_000)
        List(8) { worker ->
            launch(Dispatchers.Default) {
                repeat(500) { i -> store.save("https://example.com/$worker/$i", v1, verdict) }
            }
        }.forEach { it.join() }

        assertEquals(verdict, store.find("https://example.com/7/499", v1))
    }
}
