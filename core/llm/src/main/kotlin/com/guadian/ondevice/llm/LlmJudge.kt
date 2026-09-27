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
 * It takes the observation page inspection posted to [observations], builds the
 * model's prompt from it, runs the model, and reads the reply with [AnswerReader].
 * With no model loaded ([LlmRuntime.NONE]) it stays an uncertain `UNKNOWN`.
 * [onPrompt] sees every prompt; the app logs it.
 */
class LlmJudge(
    private val observations: ObservationBoard = ObservationBoard(),
    private val runtime: LlmRuntime = LlmRuntime.NONE,
    private val prompts: PromptBuilder = PromptBuilder(),
    private val onPrompt: (url: String, prompt: String) -> Unit = { _, _ -> },
) : Judge {
    override suspend fun judge(url: String): Verdict {
        val observation = observations.take(url) ?: return uncertain(ReasonCode.LLM_NO_OBSERVATION)
        val prompt = prompts.build(observation)
        onPrompt(url, prompt)
        val reply = runtime.complete(prompt) ?: return uncertain(ReasonCode.LLM_NOT_LOADED)
        return AnswerReader.read(reply, prompt, host = PyText.hostname(observation.finalUrl ?: url))
    }

    private fun uncertain(reason: ReasonCode) = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = Layer.LLM,
        reason = reason,
    )
}
