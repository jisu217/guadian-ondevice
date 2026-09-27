package com.guadian.ondevice.judge

import com.guadian.ondevice.store.VerdictStore

/**
 * Runs [stages] in order and returns the first `CERTAIN` verdict, or the
 * last stage's verdict when none is certain.
 *
 * [store] is itself one of the stages (placed after the deterministic rules,
 * so a cached verdict can never skip them); every certain verdict from any
 * other stage is saved back into it.
 */
class HybridJudge(
    private val stages: List<Judge>,
    private val store: VerdictStore? = null,
) : Judge {

    init {
        require(stages.isNotEmpty()) { "HybridJudge needs at least one stage." }
    }

    override suspend fun judge(url: String): Verdict {
        var last: Verdict? = null
        for (stage in stages) {
            val verdict = stage.judge(url)
            if (verdict.confidence == Confidence.CERTAIN) {
                if (verdict.layer != Layer.STORE) store?.save(url, verdict)
                return verdict
            }
            last = verdict
        }
        return checkNotNull(last)
    }
}
