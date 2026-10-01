package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Judge
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict

/**
 * Returns the first CERTAIN rule verdict; otherwise the first uncertain hint
 * (e.g. a weak brand match), so a hint never hides a later certain rule.
 */
class RuleBasedJudge(
    private val rules: List<RiskRule>,
) : Judge {

    override suspend fun judge(url: String): Verdict {
        val matches = rules.mapNotNull { it.evaluate(url) }
        return matches.firstOrNull { it.confidence == Confidence.CERTAIN }
            ?: matches.firstOrNull()
            ?: NO_MATCH
    }

    private companion object {
        val NO_MATCH = Verdict(
            risk = Risk.UNKNOWN,
            confidence = Confidence.UNCERTAIN,
            layer = Layer.RULE,
            reason = ReasonCode.NO_RULE_MATCH,
        )
    }
}
