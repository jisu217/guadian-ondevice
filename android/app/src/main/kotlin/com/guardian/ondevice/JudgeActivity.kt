package com.guardian.ondevice

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.idling.CountingIdlingResource
import com.guardian.ondevice.judge.HybridJudge
import com.guardian.ondevice.judge.LocalReasoningJudge
import com.guardian.ondevice.judge.RemoteJudge
import com.guardian.ondevice.judge.RuleBasedJudge
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.model.OnDeviceModelJudge
import com.guardian.ondevice.rag.LocalRagJudge
import com.guardian.ondevice.rules.ApkRule
import com.guardian.ondevice.rules.BrandImpersonationRule
import com.guardian.ondevice.rules.SuspiciousUrlRule
import com.guardian.ondevice.store.LocalRiskStore

class JudgeActivity : AppCompatActivity() {
    private val mainThread = Handler(Looper.getMainLooper())

    // HybridJudge runs Local Store -> Rule Engine -> On-device Model -> Local
    // RAG entirely on this thread; only RemoteJudge hops onto its own
    // executor. postToMainThread() below covers that one background hop so a
    // UI update never happens off the main thread regardless of which layer
    // resolved the verdict.
    private val hybridJudge: HybridJudge by lazy {
        HybridJudge(
            store = LocalRiskStore(),
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
                    LocalRagJudge(),
                )
            ),
            remoteJudge = RemoteJudge(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_judge)

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val resultText = findViewById<TextView>(R.id.resultText)
        val judgeButton = findViewById<Button>(R.id.judgeButton)

        judgeButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                resultText.text = "URL을 입력하세요."
                return@setOnClickListener
            }

            resultText.text = "판정 중..."
            idlingResource.increment()
            hybridJudge.judge(url) { verdict ->
                postToMainThread {
                    resultText.text = format(verdict)
                    idlingResource.decrement()
                }
            }
        }
    }

    private fun postToMainThread(action: () -> Unit) {
        mainThread.post(action)
    }

    private fun format(verdict: Verdict): String {
        return listOf(
            "위험도: ${verdict.risk}",
            "확신도: ${verdict.confidence}",
            "판정 출처: ${verdict.source}",
            "이유: ${verdict.reason}",
            "권고: ${verdict.advice}",
        ).joinToString("\n")
    }

    companion object {
        // Registered/unregistered by JudgeActivityTest around each test so
        // Espresso blocks until a judge() call's callback actually fires,
        // instead of a test guessing a sleep duration that's flaky for the
        // synchronous local layers and too short for the remote executor hop.
        @JvmField
        val idlingResource = CountingIdlingResource("JudgeActivityIdlingResource")
    }
}
