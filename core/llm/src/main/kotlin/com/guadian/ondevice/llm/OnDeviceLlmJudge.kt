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
 * It takes the observation page inspection posted to [observations] and builds
 * the model's prompt from it. No model is loaded yet, so the prompt goes to
 * [onPrompt] (the app logs it) and the verdict stays an uncertain `UNKNOWN`.
 * Output parsing, the grounding check, and the llama.cpp runtime replace this
 * behavior in later steps.
 */
class OnDeviceLlmJudge(
    private val observations: ObservationBoard = ObservationBoard(),
    private val prompts: PromptBuilder = PromptBuilder(),
    private val onPrompt: (url: String, prompt: String) -> Unit = { _, _ -> },
) : Judge {
    override suspend fun judge(url: String): Verdict {
        val observation = observations.take(url) ?: return uncertain(ReasonCode.LLM_NO_OBSERVATION)
        onPrompt(url, prompts.build(observation))
        return uncertain(ReasonCode.LLM_NOT_LOADED)
    }

    private fun uncertain(reason: ReasonCode) = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = Layer.LLM,
        reason = reason,
    )
}
