package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.util.UrlHost

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
