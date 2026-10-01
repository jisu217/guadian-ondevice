package com.guadian.ondevice.pagecontent

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import com.guadian.ondevice.llm.FormObservation
import com.guadian.ondevice.llm.PageObservation
import com.guadian.ondevice.llm.PageSignals
import com.guadian.ondevice.rules.BrandDomains
import com.guadian.ondevice.url.DomainAllowlist
import com.guadian.ondevice.url.PublicSuffixList
import com.guadian.ondevice.url.UrlHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
 * No page content leaves the device.
 *
 * Besides its own verdict, each load produces a [PageObservation] for the
 * on-device LLM stage, passed to [onObservation]. It is read with the rules the
 * model's training data was collected with (see [PageSignals]).
 */
class PageContentJudge(
    private val context: Context,
    private val officialDomains: BrandDomains = BrandDomains(),
    private val allowlist: DomainAllowlist = DomainAllowlist.default,
    private val suffixes: PublicSuffixList = PublicSuffixList.default,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /** Tests add "data" to load inline pages without a network. */
    private val allowedSchemes: Set<String> = setOf("http", "https"),
    private val onObservation: (url: String, observation: PageObservation) -> Unit = { _, _ -> },
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
        var httpStatus: Int? = null

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

        fun failLoad(detail: String?) {
            if (finished) return
            onObservation(url, PageObservation(url = url, loaded = false))
            finish(uncertain(ReasonCode.PAGE_LOAD_FAILED, detail = detail))
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
                    failLoad("too many redirects")
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, finishedUrl: String) {
                if (finished) return
                view.evaluateJavascript(INSPECTION_SCRIPT) { rawResult ->
                    val verdict = runCatching {
                        val json = payload(rawResult) ?: return@runCatching uncertain(ReasonCode.PAGE_SCRIPT_FAILED, detail = "no data")
                        onObservation(url, observe(url, finishedUrl, httpStatus ?: 200, navigations, json))
                        classify(finishedUrl, json)
                    }.getOrElse { uncertain(ReasonCode.PAGE_SCRIPT_FAILED, detail = it.javaClass.simpleName) }
                    finish(verdict)
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                // An error page still renders and is still inspected; only its status is recorded.
                if (request.isForMainFrame) httpStatus = errorResponse.statusCode
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) failLoad(error.description?.toString())
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

    private fun payload(rawResult: String?): JSONObject? = rawResult
        ?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }
        ?.let(::JSONObject)
        ?.takeUnless { it.has("error") }

    private fun observe(url: String, finalUrl: String, status: Int, redirects: Int, json: JSONObject): PageObservation {
        val text = PageSignals.collapse(json.optString("text"))
        val forms = json.optJSONArray("forms").objects().take(PageSignals.MAX_FORMS).map { form ->
            val fields = form.optJSONArray("fields").objects().map {
                PageSignals.Field(
                    type = it.optString("type"),
                    name = it.stringOrNull("name"),
                    id = it.stringOrNull("id"),
                    placeholder = it.stringOrNull("placeholder"),
                    autocomplete = it.stringOrNull("autocomplete"),
                )
            }
            FormObservation(PageSignals.formAsks(fields), PageSignals.externalAction(finalUrl, form.stringOrNull("action")))
        }
        // Anchor hrefs first, then URLs anywhere in the markup, as the collector gathered them.
        val apkLinks = LinkedHashSet<String>()
        (json.optJSONArray("apkAnchors").strings() + json.optJSONArray("apkInHtml").strings())
            .forEach { apkLinks += take(it, PageSignals.MAX_APK_CHARS) }
        return PageObservation(
            url = url,
            loaded = true,
            finalUrl = finalUrl,
            status = status,
            redirects = redirects,
            title = take(json.optString("title").trim(), PageSignals.MAX_TITLE_CHARS),
            text = take(text, PageSignals.MAX_TEXT_CHARS),
            textChars = text.codePointCount(0, text.length),
            forms = forms,
            apkLinks = apkLinks.take(PageSignals.MAX_APK_LINKS),
        )
    }

    private fun take(s: String, n: Int): String =
        if (s.codePointCount(0, s.length) <= n) s else s.substring(0, s.offsetByCodePoints(0, n))

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

    private fun classify(finishedUrl: String, json: JSONObject): Verdict {
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

        // Returns what classify() and the observation need, not the full DOM. The
        // observation fields follow the collector (ml/guadian_ml/llm/vendor/judge_agent.py):
        // text joins every text node outside script/style/noscript with spaces, as
        // BeautifulSoup's get_text(" ") does; forms, fields, and APK links use the
        // same selectors and limits. Kotlin applies the matching rules.
        const val INSPECTION_SCRIPT = """
            (function() {
                try {
                    var skip = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1 };
                    var parts = [], size = 0;
                    var walker = document.createTreeWalker(document.documentElement, NodeFilter.SHOW_TEXT, null);
                    for (var node = walker.nextNode(); node && size < 2000000; node = walker.nextNode()) {
                        var inside = false;
                        for (var p = node.parentNode; p; p = p.parentNode) { if (skip[p.nodeName]) { inside = true; break; } }
                        if (inside) continue;
                        parts.push(node.nodeValue);
                        size += node.nodeValue.length;
                    }
                    var resolve = function(href) { try { return new URL(href, location.href).href; } catch (e) { return href; } };
                    var forms = Array.prototype.slice.call(document.forms, 0, 6).map(function(form) {
                        var action = form.getAttribute('action') || '';
                        var controls = form.querySelectorAll('input, select, textarea');
                        return {
                            action: action ? resolve(action) : null,
                            fields: Array.prototype.slice.call(controls, 0, 20).map(function(el) {
                                return {
                                    type: (el.getAttribute('type') || el.tagName || '').toLowerCase(),
                                    name: el.getAttribute('name'),
                                    id: el.getAttribute('id'),
                                    placeholder: el.getAttribute('placeholder'),
                                    autocomplete: el.getAttribute('autocomplete')
                                };
                            })
                        };
                    });
                    var apkAnchors = [];
                    document.querySelectorAll('a[href]').forEach(function(a) {
                        var href = a.getAttribute('href');
                        if (/\.apk(\?|$)/i.test(href)) apkAnchors.push(resolve(href));
                    });
                    var titleEl = document.querySelector('title');
                    return JSON.stringify({
                        hasPasswordField: document.querySelectorAll('input[type="password"]').length > 0,
                        title: titleEl ? titleEl.textContent : '',
                        bodyText: (document.body ? document.body.innerText : '').slice(0, 2000),
                        text: parts.join(' '),
                        forms: forms,
                        apkAnchors: apkAnchors,
                        apkInHtml: document.documentElement.outerHTML.match(/https?:\/\/[^\s"'<>]+\.apk\b/gi) || []
                    });
                } catch (e) {
                    return JSON.stringify({ error: String(e) });
                }
            })();
        """
    }
}
