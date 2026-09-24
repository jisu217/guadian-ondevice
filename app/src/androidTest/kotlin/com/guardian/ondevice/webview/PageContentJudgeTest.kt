package com.guardian.ondevice.webview

import androidx.test.platform.app.InstrumentationRegistry
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// PageContentJudge needs a real WebView (local `test` unit tests run against
// a stub android.jar that no-ops WebView), so this runs only as an
// instrumented test. Pages are loaded from data: URLs so the test needs no
// network access and no fixture server.
// Run with: ./gradlew connectedDebugAndroidTest
class PageContentJudgeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun flagsUnverifiedLoginFormAsMedium() {
        val verdict = judgeHtml("<html><body><form><input type=\"password\"></form></body></html>")

        assertEquals(Risk.MEDIUM, verdict.risk)
        assertEquals("unverified_login_form", verdict.type)
    }

    @Test
    fun flagsBrandMismatchInPageTextAsHigh() {
        val verdict = judgeHtml(
            "<html><head><title>KakaoPay Login</title></head>" +
                "<body>Sign in to kakaopay to continue.</body></html>"
        )

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals("page_brand_impersonation", verdict.type)
    }

    @Test
    fun returnsUncertainForPlainPageWithNoSignal() {
        val verdict = judgeHtml("<html><body>Just a plain article with nothing suspicious.</body></html>")

        assertEquals(Risk.UNKNOWN, verdict.risk)
    }

    private fun judgeHtml(html: String): Verdict {
        // URLEncoder is form-encoding (space -> "+"), not URI percent-encoding;
        // a literal "+" between e.g. "input" and "type=..." would corrupt the
        // tag itself, so space must become "%20" instead.
        val encoded = URLEncoder.encode(html, "UTF-8").replace("+", "%20")
        val dataUrl = "data:text/html;charset=utf-8,$encoded"
        val judge = PageContentJudge(context = context)

        val latch = CountDownLatch(1)
        var result: Verdict? = null
        judge.judge(dataUrl) {
            result = it
            latch.countDown()
        }

        assertTrue("judge() did not call back in time", latch.await(15, TimeUnit.SECONDS))
        return result!!
    }
}
