package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict

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
