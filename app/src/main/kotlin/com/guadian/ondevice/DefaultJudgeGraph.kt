package com.guadian.ondevice

import android.content.Context
import android.util.Log
import java.io.File
import com.guadian.ondevice.classifier.OnDeviceModelJudge
import com.guadian.ondevice.judge.HybridJudge
import com.guadian.ondevice.knowledge.LocalKnowledgeJudge
import com.guadian.ondevice.llamacpp.LlamaCppRuntime
import com.guadian.ondevice.llm.ObservationBoard
import com.guadian.ondevice.llm.OnDeviceLlmJudge
import com.guadian.ondevice.pagecontent.PageContentJudge
import com.guadian.ondevice.rules.ApkRule
import com.guadian.ondevice.rules.BlocklistRule
import com.guadian.ondevice.rules.BrandImpersonationRule
import com.guadian.ondevice.rules.RuleBasedJudge
import com.guadian.ondevice.rules.SuspiciousUrlRule
import com.guadian.ondevice.store.InMemoryRiskStore
import com.guadian.ondevice.store.StoreJudge

/**
 * The app's judgment pipelines. Both share one store and one instance of
 * every stage; they differ only in whether the rendered page is inspected
 * (and so whether the LLM stage, which reads it, runs).
 */
class DefaultJudgeGraph(context: Context) {
    private val store = InMemoryRiskStore()
    private val rules = RuleBasedJudge(listOf(BlocklistRule(), ApkRule(), BrandImpersonationRule(), SuspiciousUrlRule()))
    private val cached = StoreJudge(store)
    private val model = OnDeviceModelJudge()
    private val knowledge = LocalKnowledgeJudge()
    private val observations = ObservationBoard()
    private val pageContent = PageContentJudge(context.applicationContext, onObservation = observations::post)

    // The prompt is logged so the on-device input can be checked against the
    // training format.
    private val llm = OnDeviceLlmJudge(
        observations,
        runtime = LlamaCppRuntime(File(context.filesDir, MODEL_PATH)),
        onPrompt = { url, prompt -> Log.d(LLM_LOG_TAG, "$url\n$prompt") },
    )

    /** Manual URL entry: every layer, in README order. */
    val full: HybridJudge = HybridJudge(
        stages = listOf(rules, cached, model, knowledge, pageContent, llm),
        store = store,
    )

    /**
     * Browser clicks: the browser is already loading the page, so loading it
     * a second time in a hidden WebView would only add latency. The LLM stage
     * reads what page inspection collects, so it is not part of this graph.
     */
    val shield: HybridJudge = HybridJudge(
        stages = listOf(rules, cached, model, knowledge),
        store = store,
    )

    private companion object {
        const val LLM_LOG_TAG = "GuadianLlm"

        // Copied in with adb for now (docs/architecture.md); without it the LLM stage answers "not loaded".
        const val MODEL_PATH = "models/guadian.gguf"
    }
}
