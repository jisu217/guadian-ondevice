package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlocklistRuleTest {
    private val rule = BlocklistRule()
    private val listed = File("../../model/data/fixtures/blocklist_filter_cases.csv").readLines()
        .drop(1).first { it.endsWith(",true") }.substringBefore(',')

    @Test
    fun `a listed domain is certain high`() {
        val verdict = rule.evaluate("https://$listed/login")!!

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(ReasonCode.BLOCKLISTED, verdict.reason)
        assertEquals(listed, verdict.detail)
    }

    @Test
    fun `a subdomain of a listed domain is caught`() {
        assertEquals(listed, rule.evaluate("https://secure.$listed/")?.detail)
    }

    @Test
    fun `unlisted and official domains pass`() {
        assertNull(rule.evaluate("https://www.naver.com/"))
        assertNull(rule.evaluate("https://m.kbstar.com/quics"))
    }
}
