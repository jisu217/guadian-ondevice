package com.guardian.ondevice.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalResolutionTest {
    @Test
    fun `local resolution counts are consistent`() {
        for ((_, result) in LocalResolution.measureAll()) {
            val resolved = result.stages.sumOf { it.resolved }
            assertEquals(result.total, resolved + result.unresolved)
            assertTrue(result.stages.all { it.correct <= it.resolved })
        }
    }
}
