package com.guardian.ondevice.pagecontent

import androidx.test.platform.app.InstrumentationRegistry
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.RiskCategory
import com.guardian.ondevice.judge.Verdict
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder

/**
 * PageContentJudge needs a real WebView (local `test` unit tests run against
 * a stub android.jar that no-ops WebView), so this runs only as an
 * instrumented test. Pages are loaded from data: URLs so the test needs no
 * network access and no fixture server.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
class PageContentJudgeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun flagsUnverifiedLoginFormAsMedium() {
        val verdict = judgeHtml("<html><body><form><input type=\"password\"></form></body></html>")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals(RiskCategory.UNVERIFIED_LOGIN_FORM, verdict.category)
    }

    @Test
    fun flagsBrandLoginFormOnUnofficialHostAsHigh() {
        val verdict = judgeHtml(
            "<html><head><title>KakaoPay Login</title></head>" +
                "<body>Sign in to kakaopay to continue.<form><input type=\"password\"></form></body></html>"
        )

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(RiskCategory.PAGE_BRAND_IMPERSONATION, verdict.category)
    }

    @Test
    fun brandMentionWithoutLoginFormIsNotHigh() {
        // Regression: a page that only names a brand ("Log in with Naver",
        // a news article) used to be HIGH by itself.
        val verdict = judgeHtml("<html><body>Log in with naver or read our kakao news.</body></html>")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(ReasonCode.PAGE_BRAND_MENTION_ONLY, verdict.reason)
    }

    @Test
    fun returnsUncertainForPlainPageWithNoSignal() {
        val verdict = judgeHtml("<html><body>Just a plain article with nothing suspicious.</body></html>")

        assertEquals(Risk.UNKNOWN, verdict.risk)
    }

    @Test
    fun rejectsDataUrlsByDefault() {
        val verdict = runBlocking { PageContentJudge(context = context).judge("data:text/html,<p>x</p>") }

        assertEquals(ReasonCode.PAGE_UNSUPPORTED_SCHEME, verdict.reason)
    }

    private fun judgeHtml(html: String): Verdict {
        // URLEncoder is form-encoding (space -> "+"), not URI percent-encoding;
        // a literal "+" between e.g. "input" and "type=..." would corrupt the
        // tag itself, so space must become "%20" instead.
        val encoded = URLEncoder.encode(html, "UTF-8").replace("+", "%20")
        val dataUrl = "data:text/html;charset=utf-8,$encoded"
        val judge = PageContentJudge(context = context, allowedSchemes = setOf("data"))
        return runBlocking { judge.judge(dataUrl) }
    }
}
