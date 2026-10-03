package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.url.DomainAllowlist
import com.guadian.ondevice.url.PublicSuffixList
import com.guadian.ondevice.url.UrlHost

class BrandImpersonationRule(
    private val officialDomains: BrandDomains = BrandDomains(),
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

        /*
         * Only a hint: embedded labels also occur in unrelated real sites, so
         * later layers decide. Skipped outright for allowlisted sites, checked
         * on the registrable domain so "x.github.io" does not inherit github.io.
         */
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
