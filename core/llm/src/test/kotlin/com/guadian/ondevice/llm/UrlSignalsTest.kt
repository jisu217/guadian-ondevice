package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.verdict.VerdictTable
import com.guadian.ondevice.verdict.VerdictTable.HostImitation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlSignalsTest {
    private val signals = UrlSignals()
    private val table = VerdictTable.default

    @Test
    fun `grades brand imitation in the host`() {
        val certain = signals.inspect("https://roblox.com.mu/login")
        assertEquals(HostImitation.CERTAIN, certain.facts.hostImitation)
        assertTrue(certain.details.getValue(ReasonCode.IMPERSONATION).contains("roblox.com"))

        assertEquals(HostImitation.SUSPICIOUS, signals.inspect("https://xfinityuserverification.weebly.com/").facts.hostImitation)
        assertEquals(HostImitation.NONE, signals.inspect("https://www.example.org/read").facts.hostImitation)
    }

    @Test
    fun `a blocklisted host is final at the url stage`() {
        val listed = File("../../ml/fixtures/rules/blocklist_filter_cases.csv").readLines()
            .drop(1).first { it.endsWith(",true") }.substringBefore(',')
        val evidence = signals.inspect("https://$listed/login")

        assertTrue(evidence.facts.blocklisted)
        assertEquals(listed, evidence.details[ReasonCode.BLOCKLISTED])
        assertTrue(table.decide(evidence.facts).final)
    }

    @Test
    fun `an ordinary url leaves the page unknown and the result open`() {
        val evidence = signals.inspect("https://www.example.org/read")

        assertFalse(evidence.facts.blocklisted)
        assertNull(evidence.facts.opened)
        assertFalse(table.decide(evidence.facts).final)
    }

    @Test
    fun `an apk file is settled by its own host`() {
        val offSite = table.decide(signals.inspect("https://files.example.net/app.apk").facts)
        assertEquals("HIGH" to true, offSite.risk to offSite.final)

        val official = table.decide(signals.inspect("https://www.naver.com/download/app.apk").facts)
        assertEquals("LOW" to true, official.risk to official.final)
    }
}
