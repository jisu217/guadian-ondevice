package com.guadian.ondevice.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlocklistFilterTest {
    @Test
    fun `fnv1a64 matches the published test vectors`() {
        assertEquals(0xCBF29CE484222325uL, BlocklistFilter.fnv1a64(""))
        assertEquals(0xAF63DC4C8601EC8CuL, BlocklistFilter.fnv1a64("a"))
    }

    @Test
    fun `matches the python blocklist fixture`() {
        // Written by model/training/build_blocklist_filter.py alongside the filter:
        // listed domains must hit, official domains that were never inserted must miss.
        val rows = File("../../model/data/fixtures/blocklist_filter_cases.csv").readLines()
            .drop(1).filter { it.isNotBlank() }.map { it.substringBefore(',') to it.substringAfter(',').toBoolean() }

        assertTrue(rows.count { it.second } >= 10 && rows.count { !it.second } >= 10)
        for ((domain, listed) in rows) {
            assertEquals(listed, BlocklistFilter.default.mightContain(domain), domain)
        }
    }
}
