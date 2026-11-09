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
 * Checks on an emulator which pipeline stage settles a URL (cache and URL, page,
 * LLM) and the risk the verdict table gives it. No model file is installed, so
 * these cases are settled before the LLM stage.
 * Judging runs on a background dispatcher, so each test polls the result
 * text instead of relying on Espresso's main-looper idling.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
class JudgeActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(JudgeActivity::class.java)

    @Test
    fun apkDownloadIsHighAtUrlStage() {
        val result = judge("https://unknown-site.test/download/app.apk")

        assertTrue(result, "(HIGH)" in result)
        assertTrue(result, "(url)" in result)
    }

    @Test
    fun pageThatDoesNotOpenIsMediumAtPageStage() {
        /*
         * .test is IANA-reserved (RFC 2606): guaranteed never to resolve, so
         * the page stage's WebView load fails deterministically and the table
         * settles the page as unverifiable.
         */
        val result = judge("https://plain-article.test/read")

        assertTrue(result, "(MEDIUM)" in result)
        assertTrue(result, "(page-content)" in result)
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
