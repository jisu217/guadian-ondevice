package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Judge
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict

/**
 * The on-device LLM stage, placed after page-content inspection.
 *
 * No model is loaded yet: every call returns an uncertain `UNKNOWN`, so the
 * pipeline already runs in its final shape. The prompt builder, output parser,
 * grounding check, and llama.cpp runtime replace this behavior in later steps.
 */
class OnDeviceLlmJudge : Judge {
    override suspend fun judge(url: String): Verdict = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = Layer.LLM,
        reason = ReasonCode.LLM_NOT_LOADED,
    )
}
