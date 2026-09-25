package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.RiskCategory
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.util.DomainAllowlist
import com.guardian.ondevice.util.PublicSuffixList
import com.guardian.ondevice.util.UrlHost

class BrandImpersonationRule(
    private val officialDomains: OfficialDomains = OfficialDomains(),
    private val allowlist: DomainAllowlist = DomainAllowlist.default,
    private val suffixes: PublicSuffixList = PublicSuffixList.default,
) : RiskRule {

    override fun evaluate(url: String): Verdict? {
        val host = UrlHost.of(url) ?: return null

        officialDomains.brandForOfficialHost(host)?.let { brand ->
            return verdict(Risk.LOW, Confidence.CERTAIN, ReasonCode.OFFICIAL_DOMAIN, RiskCategory.NONE, host, brand)
        }

        // A brand label as its own host token is the classic lookalike shape.
        officialDomains.brandTokenInHost(host)?.let { brand ->
            return verdict(
                Risk.HIGH, Confidence.CERTAIN, ReasonCode.BRAND_TOKEN_IN_HOST,
                RiskCategory.BRAND_IMPERSONATION, host, brand,
            )
        }

        // Only a hint: embedded labels also occur in unrelated real sites, so
        // later layers decide. Skipped outright for allowlisted sites, checked
        // on the registrable domain so "x.github.io" does not inherit github.io.
        officialDomains.brandSubstringInHost(host)?.let { brand ->
            val site = suffixes.registrableDomain(host) ?: host
            if (allowlist.containsExactly(site)) return null
            return verdict(
                Risk.MEDIUM, Confidence.UNCERTAIN, ReasonCode.BRAND_SUBSTRING_IN_HOST,
                RiskCategory.BRAND_IMPERSONATION, host, brand,
            )
        }

        return null
    }

    private fun verdict(
        risk: Risk,
        confidence: Confidence,
        reason: ReasonCode,
        category: RiskCategory,
        host: String,
        brand: String,
    ) = Verdict(
        risk = risk,
        confidence = confidence,
        layer = Layer.RULE,
        reason = reason,
        category = category,
        host = host,
        detail = brand,
    )
}
