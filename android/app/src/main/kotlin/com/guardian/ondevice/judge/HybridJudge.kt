package com.guardian.ondevice.judge

import com.guardian.ondevice.store.LocalRiskStore

class HybridJudge(
    private val store: LocalRiskStore,
    private val ruleBasedJudge: Judge,
    private val modelJudge: Judge,
    private val remoteJudge: Judge,
) : Judge {

    override fun judge(url: String, done: (Verdict) -> Unit) {
        store.find(url)?.let {
            done(it.copy(source = "local-store"))
            return
        }

        ruleBasedJudge.judge(url) { ruleVerdict ->
            if (ruleVerdict.confidence == Confidence.CERTAIN) {
                saveIfReusable(url, ruleVerdict)
                done(ruleVerdict)
                return@judge
            }

            modelJudge.judge(url) { modelVerdict ->
                if (modelVerdict.confidence == Confidence.CERTAIN) {
                    saveIfReusable(url, modelVerdict)
                    done(modelVerdict)
                    return@judge
                }

                remoteJudge.judge(url) { remoteVerdict ->
                    saveIfReusable(url, remoteVerdict)
                    done(remoteVerdict)
                }
            }
        }
    }

    private fun saveIfReusable(url: String, verdict: Verdict) {
        if (verdict.risk != Risk.UNKNOWN) {
            store.save(url, verdict)
        }
    }
}
