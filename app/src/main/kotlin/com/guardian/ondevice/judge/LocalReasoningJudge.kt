package com.guardian.ondevice.judge

class LocalReasoningJudge(
    private val judges: List<Judge>,
) : Judge {
    override fun judge(url: String, done: (Verdict) -> Unit) {
        runJudge(index = 0, url = url, lastVerdict = null, done = done)
    }

    private fun runJudge(index: Int, url: String, lastVerdict: Verdict?, done: (Verdict) -> Unit) {
        if (index >= judges.size) {
            done(
                lastVerdict ?: Verdict(
                    risk = Risk.UNKNOWN,
                    reason = "No local reasoning layer is configured.",
                    advice = "Use remote fallback if local judgment remains uncertain.",
                    source = "local-reasoning",
                    confidence = Confidence.UNCERTAIN,
                )
            )
            return
        }

        judges[index].judge(url) { verdict ->
            if (verdict.confidence == Confidence.CERTAIN) {
                done(verdict)
            } else {
                runJudge(index + 1, url, verdict, done)
            }
        }
    }
}
