package com.guadian.ondevice.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guadian.ondevice.GuadianApp
import com.guadian.ondevice.R
import com.guadian.ondevice.classifier.NearestCentroidUrlModel
import com.guadian.ondevice.judge.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JudgeActivity : AppCompatActivity() {
    private var judging: Job? = null
    private val text by lazy { VerdictText(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_judge)

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val resultText = findViewById<TextView>(R.id.resultText)
        val modelVersionText = findViewById<TextView>(R.id.modelVersionText)
        val app = application as GuadianApp

        lifecycleScope.launch {
            val version = withContext(Dispatchers.Default) { NearestCentroidUrlModel.default.version }
            modelVersionText.text = getString(R.string.model_version_label, version)
        }

        findViewById<Button>(R.id.judgeButton).setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                resultText.text = getString(R.string.judge_empty_input)
                return@setOnClickListener
            }

            judging?.cancel()
            resultText.text = getString(R.string.judge_pending)
            judging = lifecycleScope.launch {
                // Graph construction and every stage stay off the main thread;
                // PageContentJudge hops to Main by itself for its WebView.
                val verdict = withContext(Dispatchers.Default) { app.judges.full.judge(url) }
                resultText.text = format(verdict)
            }
        }
    }

    private fun format(verdict: Verdict): String {
        return listOf(
            "${getString(R.string.label_risk)}: ${text.risk(verdict.risk)} (${verdict.risk})",
            "${getString(R.string.label_confidence)}: ${text.confidence(verdict.confidence)}",
            "${getString(R.string.label_layer)}: ${text.layer(verdict.layer)} (${verdict.layer.wireName})",
            "${getString(R.string.label_reason)}: ${text.reason(verdict)}",
            "${getString(R.string.label_advice)}: ${text.advice(verdict.risk)}",
        ).joinToString("\n")
    }
}
