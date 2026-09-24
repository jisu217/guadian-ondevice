package com.guardian.ondevice

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Automates the same 4-layer check that was done by hand on an emulator with
// adb (rule engine, on-device model, local RAG, remote fallback failure).
// Run with: ./gradlew connectedDebugAndroidTest
class JudgeActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(JudgeActivity::class.java)

    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(JudgeActivity.idlingResource)
    }

    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(JudgeActivity.idlingResource)
    }

    @Test
    fun ruleEngineResolvesApkDownloadAsHigh() {
        judge("https://unknown-site.test/download/app.apk")

        onView(withId(R.id.resultText)).check(matches(withText(containsString("HIGH"))))
        onView(withId(R.id.resultText)).check(matches(withText(containsString("rule:apk"))))
    }

    @Test
    fun onDeviceModelResolvesTrustedSupportUrlAsLow() {
        judge("https://docs.example.com/support/chat?lang=ko")

        onView(withId(R.id.resultText)).check(matches(withText(containsString("LOW"))))
        onView(withId(R.id.resultText)).check(matches(withText(containsString("on-device-model"))))
    }

    @Test
    fun localRagResolvesPersonalInfoUrlAsMedium() {
        judge(
            "https://survey-form.unlisted-check.test/personal-info-phone-card" +
                "?ref=abcdefghijklmnopqrstuvwxyz1234567890"
        )

        onView(withId(R.id.resultText)).check(matches(withText(containsString("MEDIUM"))))
        onView(withId(R.id.resultText)).check(matches(withText(containsString("local-rag"))))
    }

    @Test
    fun remoteFallbackFailsSafelyToUnknown() {
        // .test is IANA-reserved (RFC 2606): guaranteed never to resolve, so
        // PageContentJudge's WebView load fails deterministically instead of
        // rendering whatever a real .org registrant happens to host there.
        judge("https://plain-article.test/read")

        onView(withId(R.id.resultText)).check(matches(withText(containsString("UNKNOWN"))))
        onView(withId(R.id.resultText)).check(matches(withText(containsString("remote"))))
    }

    private fun judge(url: String) {
        onView(withId(R.id.urlInput)).perform(replaceText(url))
        onView(withId(R.id.judgeButton)).perform(click())
    }
}
