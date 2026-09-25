package com.guardian.ondevice.store

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryRiskStoreTest {
    private val verdict = Verdict(
        risk = Risk.LOW,
        confidence = Confidence.CERTAIN,
        layer = Layer.MODEL,
        reason = ReasonCode.MODEL_CONFIDENT,
    )

    @Test
    fun `keys by normalized url, not by host`() {
        val store = InMemoryRiskStore()

        store.save("https://www.Example.com/path?utm_source=ad#frag", verdict)

        assertEquals(verdict, store.find("example.com/path"))
        assertNull(store.find("https://example.com/other"))
    }

    @Test
    fun `evicts the least recently used entry beyond capacity`() {
        val store = InMemoryRiskStore(maxEntries = 2)
        store.save("https://a.example/", verdict)
        store.save("https://b.example/", verdict)
        store.find("https://a.example/")

        store.save("https://c.example/", verdict)

        assertEquals(verdict, store.find("https://a.example/"))
        assertNull(store.find("https://b.example/"))
    }

    @Test
    fun `ignores unknown verdicts`() {
        val store = InMemoryRiskStore()

        store.save("https://example.com/", verdict.copy(risk = Risk.UNKNOWN))

        assertNull(store.find("https://example.com/"))
    }

    @Test
    fun `survives concurrent writers`() = runBlocking {
        val store = InMemoryRiskStore(maxEntries = 10_000)
        List(8) { worker ->
            launch(Dispatchers.Default) {
                repeat(500) { i -> store.save("https://example.com/$worker/$i", verdict) }
            }
        }.forEach { it.join() }

        assertEquals(verdict, store.find("https://example.com/7/499"))
    }

    @Test
    fun `store judge marks a hit as a certain store verdict`() = runBlocking {
        val store = InMemoryRiskStore()
        store.save("https://example.com/", verdict)

        val hit = StoreJudge(store).judge("https://example.com/")
        val miss = StoreJudge(store).judge("https://other.example/")

        assertEquals(Layer.STORE, hit.layer)
        assertEquals(Confidence.CERTAIN, hit.confidence)
        assertEquals(ReasonCode.CACHE_MISS, miss.reason)
        assertEquals(Confidence.UNCERTAIN, miss.confidence)
    }
}
