package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.verdict.AnswerResult
import com.guadian.ondevice.verdict.AnswerStage
import com.guadian.ondevice.verdict.Evidence

/**
 * The on-device LLM stage, after page inspection.
 *
 * It takes the observation page inspection posted to [observations], builds the
 * model's prompt from it, runs the model, and reads the reply with [AnswerReader].
 * The current model grades the risk itself, so its verdict is passed on as it is
 * ([AnswerResult.Graded]) until the question model replaces it (docs/design.md,
 * section 9). With no model loaded ([LlmRuntime.NONE]) it stays an uncertain `UNKNOWN`.
 * [onPrompt] sees every prompt; the app logs it.
 */
class LlmJudge(
    private val observations: ObservationBoard = ObservationBoard(),
    private val runtime: LlmRuntime = LlmRuntime.NONE,
    private val prompts: PromptBuilder = PromptBuilder(),
    private val onPrompt: (url: String, prompt: String) -> Unit = { _, _ -> },
) : AnswerStage {
    override suspend fun answer(url: String, known: Evidence): AnswerResult = AnswerResult.Graded(judge(url))

    suspend fun judge(url: String): Verdict {
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
