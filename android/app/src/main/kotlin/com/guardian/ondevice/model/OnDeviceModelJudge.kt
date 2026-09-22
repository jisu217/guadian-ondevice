package com.guardian.ondevice.model

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict

class OnDeviceModelJudge : Judge {
    override fun judge(url: String, done: (Verdict) -> Unit) {
        // TODO: Add TFLite/ONNX URL risk classifier.
        done(
            Verdict(
                risk = Risk.UNKNOWN,
                reason = "On-device model is not connected yet.",
                advice = "Use knowledge retrieval or remote fallback.",
                source = "on-device-model",
                confidence = Confidence.UNCERTAIN,
            )
        )
    }
}

