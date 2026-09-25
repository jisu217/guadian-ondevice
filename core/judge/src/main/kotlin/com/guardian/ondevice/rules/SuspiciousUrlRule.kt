package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.RiskCategory
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.util.UrlHost

class SuspiciousUrlRule : RiskRule {
    // Whole-token match on path/query: substring matching flagged "/prevention"
    // ("event") and "/accounting" ("account").
    override fun evaluate(url: String): Verdict? {
        if (!UrlRuleSignals.hasSuspiciousToken(url)) return null

        return Verdict(
            risk = Risk.MEDIUM,
            confidence = Confidence.CERTAIN,
            layer = Layer.RULE,
            reason = ReasonCode.SUSPICIOUS_KEYWORD,
            category = RiskCategory.SUSPICIOUS_URL,
            host = UrlHost.of(url),
        )
    }
}
