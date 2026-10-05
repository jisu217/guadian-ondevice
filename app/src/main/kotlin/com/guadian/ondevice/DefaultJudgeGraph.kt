package com.guadian.ondevice

import android.content.Context
import android.util.Log
import java.io.File
import com.guadian.ondevice.llamacpp.LlamaCppRuntime
import com.guadian.ondevice.llm.LlmJudge
import com.guadian.ondevice.llm.ObservationBoard
import com.guadian.ondevice.llm.UrlSignals
import com.guadian.ondevice.pagecontent.PageInspector
import com.guadian.ondevice.store.InMemoryVerdictStore
import com.guadian.ondevice.verdict.VerdictPipeline

/**
 * The app's judgment pipelines (docs/design.md, section 3). Both share one store,
 * one verdict table, and one instance of every stage; they differ only in whether
 * the on-device LLM runs.
 */
class DefaultJudgeGraph(context: Context) {
    private val store = InMemoryVerdictStore()
    private val urlSignals = UrlSignals()
    private val observations = ObservationBoard()
    private val page = PageInspector(context.applicationContext, onObservation = observations::post)

    /*
     * The prompt is logged so the on-device input can be checked against the
     * training format.
     */
    private val llm = LlmJudge(
        observations,
        runtime = LlamaCppRuntime(File(context.filesDir, MODEL_PATH)),
        onPrompt = { url, prompt -> Log.d(LLM_LOG_TAG, "$url\n$prompt") },
    )

    /** Manual URL entry: cache and URL, page, on-device LLM, verdict. */
    val full = VerdictPipeline(url = urlSignals, page = page, answers = llm, store = store)

    /**
     * Browser clicks: cache and URL, then page; a result still not final is
     * `UNKNOWN`. Whether Shield also runs the model is decided once the model is
     * timed on a phone (docs/design.md, section 10).
     */
    val shield = VerdictPipeline(url = urlSignals, page = page, answers = null, store = store)

    private companion object {
        const val LLM_LOG_TAG = "GuadianLlm"

        // Copied in with adb for now (docs/architecture.md); without it the LLM stage answers "not loaded".
        const val MODEL_PATH = "models/guadian.gguf"
    }
}
