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
 * model's prompt from it, and runs the model. A question model's reply is read with
 * [AnswerReader.readAnswers] and its answers go to the verdict table
 * ([AnswerResult.Answered]); a reply that grades the risk itself (run 3 and earlier)
 * is read with [AnswerReader.read] and passed on as it is ([AnswerResult.Graded]), so
 * the shipped model keeps working until a question model replaces it. An unreadable
 * reply is an uncertain `MEDIUM`; with no model loaded ([LlmRuntime.NONE]) the stage
 * stays an uncertain `UNKNOWN`. [onPrompt] sees every prompt; the app logs it.
 */
class LlmJudge(
    private val observations: ObservationBoard = ObservationBoard(),
    private val runtime: LlmRuntime = LlmRuntime.NONE,
    private val prompts: PromptBuilder = PromptBuilder(),
    private val brands: LlmBrands = LlmBrands.default,
    private val onPrompt: (url: String, prompt: String) -> Unit = { _, _ -> },
) : AnswerStage {
    override suspend fun answer(url: String, known: Evidence): AnswerResult {
        val observation = observations.take(url) ?: return AnswerResult.Graded(uncertain(ReasonCode.LLM_NO_OBSERVATION))
        val prompt = prompts.build(observation)
        onPrompt(url, prompt)
        val reply = runtime.complete(prompt) ?: return AnswerResult.Graded(uncertain(ReasonCode.LLM_NOT_LOADED))
        val landingUrl = if (observation.loaded) observation.finalUrl ?: url else url
        if (AnswerReader.isGraded(reply)) {
            return AnswerResult.Graded(AnswerReader.read(reply, prompt, host = PyText.hostname(landingUrl)))
        }
        val answers = AnswerReader.readAnswers(reply, prompt)
            ?: return AnswerResult.Graded(AnswerReader.read(reply, prompt, host = PyText.hostname(landingUrl)))
        val facts = known.facts.copy(
            claim = brands.claimKind(answers.claims, PyText.asciiHost(landingUrl)),
            signIn = answers.signIn,
            payment = answers.payment,
            investment = answers.investment,
            rewardPressure = answers.rewardPressure,
            contentFarm = answers.contentFarm,
        )
        val details = if (answers.claims != null) known.details + (ReasonCode.IMPERSONATION to answers.evidence) else known.details
        return AnswerResult.Answered(known.copy(facts = facts, details = details))
    }

    private fun uncertain(reason: ReasonCode) = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = Layer.LLM,
        reason = reason,
    )
}
