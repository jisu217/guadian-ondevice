package com.guardian.ondevice.judge

import com.guardian.ondevice.rules.RiskRule

class RuleBasedJudge(
    private val rules: List<RiskRule>,
) : Judge {

    override fun judge(url: String, done: (Verdict) -> Unit) {
        val verdict = rules.firstNotNullOfOrNull { it.evaluate(url) }
            ?: Verdict(
                risk = Risk.UNKNOWN,
                reason = "No deterministic local rule matched.",
                advice = "Use model or remote fallback.",
                source = "rule-engine",
                confidence = Confidence.UNCERTAIN,
            )

        done(verdict)
    }
}

