package com.guadian.ondevice.rules

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UrlRuleSignals.hasApk must agree with the shared rule fixture. */
class RuleParityTest {
    @Test
    fun `matches the shared rule parity fixture`() {
        val lines = File("../../ml/fixtures/rules/rule_parity_cases.csv").readLines().filter { it.isNotBlank() }
        assertEquals("url,category", lines.first())
        val rows = lines.drop(1).map { it.substringBeforeLast(',') to it.substringAfterLast(',') }

        assertTrue(rows.isNotEmpty())
        for ((url, expected) in rows) {
            assertEquals(expected == "apk", UrlRuleSignals.hasApk(url), url)
        }
    }
}
