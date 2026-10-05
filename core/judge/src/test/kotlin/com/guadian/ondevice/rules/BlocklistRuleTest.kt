package com.guadian.ondevice.rules

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlocklistRuleTest {
    private val rule = BlocklistRule()
    private val listed = File("../../ml/fixtures/rules/blocklist_filter_cases.csv").readLines()
        .drop(1).first { it.endsWith(",true") }.substringBefore(',')

    @Test
    fun `a listed domain is found`() {
        assertEquals(listed, rule.listedDomain("https://$listed/login"))
    }

    @Test
    fun `a subdomain of a listed domain is caught`() {
        assertEquals(listed, rule.listedDomain("https://secure.$listed/"))
    }

    @Test
    fun `unlisted and official domains pass`() {
        assertNull(rule.listedDomain("https://www.naver.com/"))
        assertNull(rule.listedDomain("https://m.kbstar.com/quics"))
    }
}
