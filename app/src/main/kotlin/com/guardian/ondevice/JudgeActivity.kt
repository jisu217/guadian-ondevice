package com.guardian.ondevice

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.idling.CountingIdlingResource
import com.guardian.ondevice.judge.DefaultJudgeGraph
import com.guardian.ondevice.judge.HybridJudge
import com.guardian.ondevice.judge.Verdict

class JudgeActivity : AppCompatActivity() {
    private val mainThread = Handler(Looper.getMainLooper())

    private val hybridJudge: HybridJudge by lazy {
        DefaultJudgeGraph.create(context = this)
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
        // Registered by JudgeActivityTest so Espresso waits for judge callbacks.
        @JvmField
        val idlingResource = CountingIdlingResource("JudgeActivityIdlingResource")
    }
}
