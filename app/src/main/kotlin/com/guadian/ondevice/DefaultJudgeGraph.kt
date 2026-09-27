package com.guadian.ondevice

import android.content.Context
import com.guadian.ondevice.classifier.OnDeviceModelJudge
import com.guadian.ondevice.judge.HybridJudge
import com.guadian.ondevice.knowledge.LocalKnowledgeJudge
import com.guadian.ondevice.pagecontent.PageContentJudge
import com.guadian.ondevice.remote.RemoteJudge
import com.guadian.ondevice.rules.ApkRule
import com.guadian.ondevice.rules.BrandImpersonationRule
import com.guadian.ondevice.rules.RuleBasedJudge
import com.guadian.ondevice.rules.SuspiciousUrlRule
import com.guadian.ondevice.store.InMemoryRiskStore
import com.guadian.ondevice.store.StoreJudge

/**
 * The app's judgment pipelines. Both share one store and one instance of
 * every stage; they differ only in whether the rendered page is inspected.
 */
class DefaultJudgeGraph(context: Context) {
    private val store = InMemoryRiskStore()
    private val rules = RuleBasedJudge(listOf(ApkRule(), BrandImpersonationRule(), SuspiciousUrlRule()))
    private val cached = StoreJudge(store)
    private val model = OnDeviceModelJudge()
    private val knowledge = LocalKnowledgeJudge()
    private val pageContent = PageContentJudge(context.applicationContext)
    private val remote = RemoteJudge(BuildConfig.REMOTE_JUDGE_URL)

    /** Manual URL entry: every layer, in README order. */
    val full: HybridJudge = HybridJudge(
        stages = listOf(rules, cached, model, knowledge, pageContent, remote),
        store = store,
    )

    /**
     * Browser clicks: the browser is already loading the page, so loading it
     * a second time in a hidden WebView would only add latency.
     */
    val shield: HybridJudge = HybridJudge(
        stages = listOf(rules, cached, model, knowledge, remote),
        store = store,
    )
}
