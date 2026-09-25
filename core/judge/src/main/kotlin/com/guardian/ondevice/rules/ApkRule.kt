package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.RiskCategory
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.util.UrlHost

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
