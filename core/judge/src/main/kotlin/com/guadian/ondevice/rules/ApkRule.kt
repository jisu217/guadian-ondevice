package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.util.UrlHost

class ApkRule : RiskRule {
    override fun evaluate(url: String): Verdict? {
        if (!UrlRuleSignals.hasApk(url)) return null

        return Verdict(
            risk = Risk.HIGH,
            confidence = Confidence.CERTAIN,
            layer = Layer.RULE,
            reason = ReasonCode.APK_DOWNLOAD,
            category = RiskCategory.APK,
            host = UrlHost.of(url),
        )
    }
}
