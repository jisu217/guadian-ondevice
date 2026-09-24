package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.util.UrlHost

class BrandImpersonationRule(
    private val officialDomains: OfficialDomains = OfficialDomains(),
) : RiskRule {

    override fun evaluate(url: String): Verdict? {
        val host = UrlHost.of(url) ?: return null
        val knownBrand = officialDomains.brandForOfficialHost(host)
        if (knownBrand != null) {
            return Verdict(
                risk = Risk.LOW,
                reason = "The host matches a known official domain.",
                advice = "Allow the navigation.",
                source = "rule:official-domain",
                site = host,
            )
        }

        val mentionedBrand = officialDomains.brandMentionedInHost(host)
        if (mentionedBrand != null) {
            return Verdict(
                risk = Risk.HIGH,
                reason = "The host mentions a known brand but does not match an official domain.",
                advice = "Block or strongly warn before opening.",
                source = "rule:brand-impersonation",
                confidence = Confidence.CERTAIN,
                site = host,
                type = "brand_impersonation",
            )
        }

        return null
    }
}
