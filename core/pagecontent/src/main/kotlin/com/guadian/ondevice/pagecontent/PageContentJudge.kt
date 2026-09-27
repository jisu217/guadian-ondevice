package com.guadian.ondevice.pagecontent

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Judge
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.rules.OfficialDomains
import com.guadian.ondevice.util.DomainAllowlist
import com.guadian.ondevice.util.PublicSuffixList
import com.guadian.ondevice.util.UrlHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * On-device rendered-page inspection for cases URL-only layers cannot resolve.
 *
 * Trade-off: this loads the candidate page itself, so the destination server
 * sees a visit from the device. The WebView is locked down (no downloads,
 * popups, cookies, storage, images, or non-http(s) navigation, bounded
 * redirects), but JavaScript stays on because evaluateJavascript needs it.
 * No page content is sent to the remote fallback server.
 */
class PageContentJudge(
    private val context: Context,
    private val officialDomains: OfficialDomains = OfficialDomains(),
    private val allowlist: DomainAllowlist = DomainAllowlist.default,
    private val suffixes: PublicSuffixList = PublicSuffixList.default,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /** Tests add "data" to load inline pages without a network. */
    private val allowedSchemes: Set<String> = setOf("http", "https"),
) : Judge {
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun judge(url: String): Verdict {
        if (schemeOf(url) !in allowedSchemes) return uncertain(ReasonCode.PAGE_UNSUPPORTED_SCHEME)
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) { inspect(url) }
        } ?: uncertain(ReasonCode.PAGE_TIMEOUT)
    }

    private suspend fun inspect(url: String): Verdict = suspendCancellableCoroutine { cont ->
        val webView = WebView(context)
        var finished = false
        var navigations = 0

        fun release() {
            if (finished) return
            finished = true
            webView.stopLoading()
            webView.destroy()
        }

        fun finish(verdict: Verdict) {
            if (finished) return
            release()
            if (cont.isActive) cont.resume(verdict)
        }

        // Cancellation (e.g. the timeout) can arrive on any thread; WebView
        // must be torn down on the main thread.
        cont.invokeOnCancellation { mainHandler.post { release() } }

        lockDown(webView)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (schemeOf(request.url.toString()) !in allowedSchemes) return true
                navigations++
                if (navigations > MAX_REDIRECTS) {
                    finish(uncertain(ReasonCode.PAGE_LOAD_FAILED, detail = "too many redirects"))
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (finished) return
                view.evaluateJavascript(INSPECTION_SCRIPT) { rawResult ->
                    val verdict = runCatching { classify(url, rawResult) }
                        .getOrElse { uncertain(ReasonCode.PAGE_SCRIPT_FAILED, detail = it.javaClass.simpleName) }
                    finish(verdict)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    finish(uncertain(ReasonCode.PAGE_LOAD_FAILED, detail = error.description?.toString()))
                }
            }
        }

        webView.loadUrl(url)
    }

    private fun lockDown(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.setDownloadListener { _, _, _, _, _ -> }
        // This app hosts no other web content, so refusing cookies app-wide is safe.
        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(webView, false)
        }
    }

    private fun classify(finishedUrl: String, rawResult: String?): Verdict {
        val payload = rawResult
            ?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }
            ?: return uncertain(ReasonCode.PAGE_SCRIPT_FAILED, detail = "no data")
        val json = JSONObject(payload)

        val host = UrlHost.of(finishedUrl)
        val officialBrand = host?.let { officialDomains.brandForOfficialHost(it) }
        val mentionedBrand = officialDomains.brandMentionedInText("${json.optString("title")} ${json.optString("bodyText")}")
        val hasPassword = json.optBoolean("hasPasswordField")
        val allowlisted = host?.let { allowlist.containsExactly(suffixes.registrableDomain(it) ?: it) } ?: false

        if (hasPassword && officialBrand == null && !allowlisted) {
            // A brand name alone is common ("Log in with Naver"); a brand name
            // next to a password field on an unrelated host is the phishing shape.
            return if (mentionedBrand != null) {
                certain(Risk.HIGH, ReasonCode.PAGE_BRAND_LOGIN_FORM, RiskCategory.PAGE_BRAND_IMPERSONATION, host, mentionedBrand)
            } else {
                certain(Risk.MEDIUM, ReasonCode.PAGE_UNVERIFIED_LOGIN_FORM, RiskCategory.UNVERIFIED_LOGIN_FORM, host, null)
            }
        }

        if (mentionedBrand != null && mentionedBrand != officialBrand) {
            return uncertain(ReasonCode.PAGE_BRAND_MENTION_ONLY, host = host, detail = mentionedBrand)
        }

        return uncertain(ReasonCode.PAGE_NO_SIGNAL, host = host)
    }

    private fun certain(risk: Risk, reason: ReasonCode, category: RiskCategory, host: String?, detail: String?) =
        Verdict(
            risk = risk,
            confidence = Confidence.CERTAIN,
            layer = Layer.PAGE_CONTENT,
            reason = reason,
            category = category,
            host = host,
            detail = detail,
        )

    private fun uncertain(reason: ReasonCode, host: String? = null, detail: String? = null) = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = Layer.PAGE_CONTENT,
        reason = reason,
        host = host,
        detail = detail,
    )

    private fun schemeOf(url: String): String? = url.trim().substringBefore(':', "").lowercase().ifEmpty { null }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val MAX_REDIRECTS = 5

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
