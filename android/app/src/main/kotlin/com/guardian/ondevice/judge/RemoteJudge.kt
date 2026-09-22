package com.guardian.ondevice.judge

class RemoteJudge : Judge {
    override fun judge(url: String, done: (Verdict) -> Unit) {
        // TODO: Port the existing HttpJudge API client from reference/HttpJudge.kt.
        done(
            Verdict(
                risk = Risk.UNKNOWN,
                reason = "Remote judge is not connected yet.",
                advice = "Keep the user in the safe confirmation flow.",
                source = "remote",
                confidence = Confidence.UNCERTAIN,
            )
        )
    }
}

