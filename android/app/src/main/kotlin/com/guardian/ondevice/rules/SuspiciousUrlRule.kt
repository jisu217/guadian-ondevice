package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict

class SuspiciousUrlRule : RiskRule {
    private val suspiciousTokens = listOf("free", "event", "gift", "loan", "verify", "account")

    override fun evaluate(url: String): Verdict? {
        val normalized = url.lowercase()
        if (suspiciousTokens.none { it in normalized }) return null

        return Verdict(
            risk = Risk.MEDIUM,
            reason = "The URL contains a suspicious landing-page keyword.",
            advice = "Ask the user to confirm before opening.",
            source = "rule:suspicious-url",
            type = "suspicious_url",
        )
    }
}

