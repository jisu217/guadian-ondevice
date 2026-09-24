package com.guardian.ondevice.store

import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryRiskStoreTest {
    @Test
    fun `stores verdict by normalized host`() {
        val store = InMemoryRiskStore()
        val verdict = Verdict(
            risk = Risk.LOW,
            reason = "Official domain.",
            advice = "Allow.",
            source = "rule:official-domain",
        )

        store.save("https://www.example.com/path", verdict)

        assertEquals(verdict, store.find("example.com/other"))
    }
}
