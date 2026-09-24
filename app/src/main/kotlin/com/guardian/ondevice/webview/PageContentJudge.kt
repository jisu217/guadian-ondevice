package com.guardian.ondevice.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.rules.OfficialDomains
import com.guardian.ondevice.util.UrlHost
import org.json.JSONObject
import org.json.JSONTokener

/**
 * On-device rendered-page inspection for cases URL-only layers cannot resolve.
 *
 * Runs late in the local chain because it pays the cost of loading a WebView.
 * It sends no page content to this project's remote fallback server.
 */
class PageContentJudge(
    private val context: Context,
    private val officialDomains: OfficialDomains = OfficialDomains(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val mainThread: Handler = Handler(Looper.getMainLooper()),
) : Judge {

    override fun judge(url: String, done: (Verdict) -> Unit) {
        if (!isInspectable(url)) {
            done(uncertain("Page content inspection only supports http(s) URLs."))
            return
        }
        mainThread.post { inspect(url, done) }
    }

    private fun inspect(url: String, done: (Verdict) -> Unit) {
        val webView = WebView(context)
        var finished = false

        fun finishOnce(verdict: Verdict) {
            if (finished) return
            finished = true
            mainThread.removeCallbacksAndMessages(webView)
            webView.stopLoading()
            webView.destroy()
            done(verdict)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(INSPECTION_SCRIPT) { rawResult ->
                    val verdict = runCatching { classify(url, rawResult) }
                        .getOrElse { uncertain("Failed to interpret page content: ${it.javaClass.simpleName}.") }
                    finishOnce(verdict)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    finishOnce(uncertain("Page failed to load: ${error.description}."))
                }
            }
        }

        // Use the WebView as the Handler token so finishOnce can cancel timeout.
        mainThread.postAtTime(
            { finishOnce(uncertain("Page content inspection timed out.")) },
            webView,
            SystemClock.uptimeMillis() + timeoutMs,
        )

        webView.loadUrl(url)
    }

    private fun classify(finishedUrl: String, rawResult: String?): Verdict {
        val payload = rawResult
            ?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }
            ?: return uncertain("Page content script returned no data.")
        val json = JSONObject(payload)

        val host = UrlHost.of(finishedUrl)
        val officialBrand = host?.let { officialDomains.brandForOfficialHost(it) }
        val pageText = "${json.optString("title")} ${json.optString("bodyText")}"
        val mentionedBrand = officialDomains.brandMentionedInText(pageText)

        if (mentionedBrand != null && mentionedBrand != officialBrand) {
            return Verdict(
                risk = Risk.HIGH,
                reason = "Page content mentions '$mentionedBrand' but is hosted on " +
                    "'${host ?: finishedUrl}', which is not that brand's official domain.",
                advice = "Do not enter credentials or payment details on this page.",
                source = "page-content",
                confidence = Confidence.CERTAIN,
                site = host,
                type = "page_brand_impersonation",
            )
        }

        if (json.optBoolean("hasPasswordField") && officialBrand == null) {
            return Verdict(
                risk = Risk.MEDIUM,
                reason = "The rendered page shows a password field on a host with no known official-domain match.",
                advice = "Verify the domain carefully before entering a password.",
                source = "page-content",
                confidence = Confidence.CERTAIN,
                site = host,
                type = "unverified_login_form",
            )
        }

        return uncertain("Page content did not match any known risk pattern.", site = host)
    }

    private fun uncertain(reason: String, site: String? = null): Verdict {
        return Verdict(
            risk = Risk.UNKNOWN,
            reason = reason,
            advice = "Use remote fallback if page content inspection remains uncertain.",
            source = "page-content",
            confidence = Confidence.UNCERTAIN,
            site = site,
        )
    }

    private fun isInspectable(url: String): Boolean {
        val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https" || scheme == "data"
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L

        // Returns only what classify() needs, not the full DOM.
        const val INSPECTION_SCRIPT = """
            (function() {
                try {
                    return JSON.stringify({
                        hasPasswordField: document.querySelectorAll('input[type="password"]').length > 0,
                        title: document.title || '',
                        bodyText: (document.body ? document.body.innerText : '').slice(0, 2000)
                    });
                } catch (e) {
                    return JSON.stringify({ error: String(e) });
                }
            })();
        """
    }
}
