package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.RiskCategory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ApkRule and SuspiciousUrlRule must produce the categories in the rule fixture. */
class RuleParityTest {
    private val rules = listOf(ApkRule(), SuspiciousUrlRule())

    @Test
    fun `matches the shared rule parity fixture`() {
        val lines = File("../../model/data/fixtures/rule_parity_cases.csv").readLines().filter { it.isNotBlank() }
        assertEquals("url,category", lines.first())
        val rows = lines.drop(1).map { it.substringBeforeLast(',') to it.substringAfterLast(',') }

        assertTrue(rows.isNotEmpty())
        for ((url, expected) in rows) {
            val actual = rules.firstNotNullOfOrNull { it.evaluate(url) }?.category ?: RiskCategory.NONE
            assertEquals(expected, actual.wireName, url)
        }
    }
}
