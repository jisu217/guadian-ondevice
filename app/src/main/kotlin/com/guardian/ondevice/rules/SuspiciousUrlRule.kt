package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict

class SuspiciousUrlRule : RiskRule {
    private val suspiciousTokens = listOf("free", "event", "gift", "loan", "verify", "account")

    override fun evaluate(url: String): Verdict? {
        // Matching the whole URL flags legitimate hosts like "myaccount.google.com".
        // These tokens describe a landing-page lure, so only the path/query should count.
        val target = pathAndQueryOf(url).lowercase()
        if (suspiciousTokens.none { it in target }) return null

        return Verdict(
            risk = Risk.MEDIUM,
            reason = "The URL contains a suspicious landing-page keyword.",
            advice = "Ask the user to confirm before opening.",
            source = "rule:suspicious-url",
            type = "suspicious_url",
        )
    }

    private fun pathAndQueryOf(url: String): String {
        val trimmed = url.trim()
        val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return trimmed
        return "${uri.path.orEmpty()}?${uri.query.orEmpty()}"
    }
}

