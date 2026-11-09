package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.rules.BlocklistRule
import com.guadian.ondevice.rules.UrlRuleSignals
import com.guadian.ondevice.url.UrlHost
import com.guadian.ondevice.verdict.Evidence
import com.guadian.ondevice.verdict.UrlStage
import com.guadian.ondevice.verdict.VerdictTable.Facts
import com.guadian.ondevice.verdict.VerdictTable.HostImitation

/**
 * The URL stage's signals (docs/design.md, section 4): the blocklist and brand
 * imitation in the host, by the same [HostRules] the training labels use.
 *
 * A URL that is itself an `.apk` file opens no page to redirect from, so its own host
 * decides whether it is the brand's site, and the verdict table settles it here.
 */
class UrlSignals(
    private val blocklist: BlocklistRule = BlocklistRule(),
    private val hostRules: HostRules = HostRules(),
    private val brands: LlmBrands = LlmBrands.default,
) : UrlStage {
    override fun inspect(url: String): Evidence {
        val details = mutableMapOf<ReasonCode, String>()
        val listed = blocklist.listedDomain(url)?.also { details[ReasonCode.BLOCKLISTED] = it }
        val imitation = hostRules.gradeHost(url)?.also { details += imitationDetails(it) }
        var facts = Facts(blocklisted = listed != null, hostImitation = hostImitation(imitation))
        if (UrlRuleSignals.hasApk(url)) {
            val brand = brands.officialSiteOf(PyText.asciiHost(url))?.also { details[ReasonCode.OFFICIAL_SITE] = it }
            facts = facts.copy(opened = true, official = brand != null, apkLink = true)
        }
        return Evidence(facts, host = UrlHost.of(url), details = details)
    }

    companion object {
        fun hostImitation(verdict: HostRules.HostVerdict?): HostImitation = when (verdict?.risk) {
            "HIGH" -> HostImitation.CERTAIN
            "MEDIUM" -> HostImitation.SUSPICIOUS
            else -> HostImitation.NONE
        }

        fun imitationDetails(verdict: HostRules.HostVerdict) =
            mapOf(ReasonCode.IMPERSONATION to verdict.reason, ReasonCode.SUSPICIOUS_HOST to verdict.reason)
    }
}
