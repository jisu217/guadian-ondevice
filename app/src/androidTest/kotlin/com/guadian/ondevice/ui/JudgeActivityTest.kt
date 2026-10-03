package com.guadian.ondevice.ui

import android.os.SystemClock
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.guadian.ondevice.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Automates the layer check that was done by hand on an emulator with adb
 * (rule engine, local knowledge, page content, LLM).
 * Judging runs on a background dispatcher, so each test polls the result
 * text instead of relying on Espresso's main-looper idling.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
class JudgeActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(JudgeActivity::class.java)

    @Test
    fun ruleEngineResolvesApkDownloadAsHigh() {
        val result = judge("https://unknown-site.test/download/app.apk")

        assertTrue(result, "(HIGH)" in result)
        assertTrue(result, "(rule)" in result)
    }

    @Test
    fun localKnowledgeResolvesPersonalInfoUrlAsMedium() {
        val result = judge(
            "https://survey-form.unlisted-check.test/personal-info-phone-card" +
                "?ref=abcdefghijklmnopqrstuvwxyz1234567890"
        )

        assertTrue(result, "(MEDIUM)" in result)
        assertTrue(result, "(local-knowledge)" in result)
    }

    @Test
    fun unresolvableUrlEndsUnknownAtLlmStage() {
        /*
         * .test is IANA-reserved (RFC 2606): guaranteed never to resolve, so
         * PageContentJudge's WebView load fails deterministically instead of
         * rendering whatever a real .org registrant happens to host there.
         */
        val result = judge("https://plain-article.test/read")

        assertTrue(result, "(UNKNOWN)" in result)
        assertTrue(result, "(llm)" in result)
    }

    private fun judge(url: String): String {
        onView(withId(R.id.urlInput)).perform(replaceText(url))
        onView(withId(R.id.judgeButton)).perform(click())
        return awaitResult()
    }

    /** Polls until the result text leaves the pending state. */
    private fun awaitResult(timeoutMs: Long = 20_000): String {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var text = ""
        while (SystemClock.uptimeMillis() < deadline) {
            activityRule.scenario.onActivity { activity ->
                val view = activity.findViewById<TextView>(R.id.resultText)
                text = view.text.toString()
            }
            val pending = activity { getString(R.string.judge_pending) }
            if (text.isNotEmpty() && text != pending) return text
            SystemClock.sleep(100)
        }
        error("Judgment did not finish within ${timeoutMs}ms; last text: $text")
    }

    private fun <T> activity(block: JudgeActivity.() -> T): T {
        var result: T? = null
        activityRule.scenario.onActivity { result = it.block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
