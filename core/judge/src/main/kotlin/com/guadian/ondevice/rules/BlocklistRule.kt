package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.util.BlocklistFilter
import com.guadian.ondevice.util.PublicSuffixList
import com.guadian.ondevice.util.UrlHost

/**
 * HIGH when the host or one of its parent domains is on the bundled blocklist.
 *
 * The walk stops at the registrable domain, so a listed `evil.example` also
 * catches `login.evil.example`, but nothing above it (such as a shared hosting
 * suffix) is ever consulted.
 */
class BlocklistRule(
    private val filter: BlocklistFilter = BlocklistFilter.default,
    private val suffixes: PublicSuffixList = PublicSuffixList.default,
) : RiskRule {
    override fun evaluate(url: String): Verdict? {
        val host = UrlHost.of(url) ?: return null
        val registrable = suffixes.registrableDomain(host) ?: return null
        val listed = generateSequence(host) { it.substringAfter('.', "").ifEmpty { null } }
            .takeWhile { it.length >= registrable.length }
            .firstOrNull(filter::mightContain)
            ?: return null

        return Verdict(
            risk = Risk.HIGH,
            confidence = Confidence.CERTAIN,
            layer = Layer.RULE,
            reason = ReasonCode.BLOCKLISTED,
            category = RiskCategory.BLOCKLISTED,
            host = host,
            detail = listed,
        )
    }
}
