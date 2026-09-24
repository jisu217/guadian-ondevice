package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict

class ApkRule : RiskRule {
    override fun evaluate(url: String): Verdict? {
        val normalized = url.lowercase()
        if (!normalized.contains(".apk")) return null

        return Verdict(
            risk = Risk.HIGH,
            reason = "The URL appears to download an APK file outside a trusted app store.",
            advice = "Block the navigation before installation.",
            source = "rule:apk",
            // Matches server/judge_service.py's local precheck and system
            // prompt vocabulary for the same category -- Verdict.type is
            // meant to be one shared taxonomy across local and remote.
            type = "apk",
        )
    }
}

