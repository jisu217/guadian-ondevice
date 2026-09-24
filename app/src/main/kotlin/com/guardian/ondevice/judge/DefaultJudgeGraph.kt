package com.guardian.ondevice.judge

import android.content.Context
import com.guardian.ondevice.knowledge.LocalKnowledgeJudge
import com.guardian.ondevice.model.OnDeviceModelJudge
import com.guardian.ondevice.rules.ApkRule
import com.guardian.ondevice.rules.BrandImpersonationRule
import com.guardian.ondevice.rules.SuspiciousUrlRule
import com.guardian.ondevice.store.InMemoryRiskStore
import com.guardian.ondevice.webview.PageContentJudge

object DefaultJudgeGraph {
    fun create(context: Context): HybridJudge {
        return HybridJudge(
            store = InMemoryRiskStore(),
            ruleBasedJudge = RuleBasedJudge(
                rules = listOf(
                    ApkRule(),
                    BrandImpersonationRule(),
                    SuspiciousUrlRule(),
                )
            ),
            localReasoningJudge = LocalReasoningJudge(
                judges = listOf(
                    OnDeviceModelJudge(),
                    LocalKnowledgeJudge(),
                    PageContentJudge(context = context),
                )
            ),
            remoteJudge = RemoteJudge(),
        )
    }
}
