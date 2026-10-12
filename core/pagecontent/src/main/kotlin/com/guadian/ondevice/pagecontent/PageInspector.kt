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
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.llm.PageFacts
import com.guadian.ondevice.llm.PageObservation
import com.guadian.ondevice.llm.PageSignals
import com.guadian.ondevice.verdict.Evidence
import com.guadian.ondevice.verdict.PageResult
import com.guadian.ondevice.verdict.PageStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * The page stage (docs/design.md, section 3): opens the page in a hidden WebView,
 * runs `page_inspection.js`, and turns what it saw into page signals with
 * [PageFacts]. It decides nothing; the verdict table does.
 *
 * Trade-off: this loads the candidate page itself, so the destination server
 * sees a visit from the device. The WebView is locked down (no downloads,
 * popups, cookies, storage, images, or non-http(s) navigation, bounded
 * redirects), but JavaScript stays on because evaluateJavascript needs it.
 * No page content leaves the device.
 *
 * Each load also produces a [PageObservation] for the on-device LLM stage, passed
 * to [onObservation]. It is read with the rules the model's training data was
 * collected with (see [PageSignals]). A page that fails to load or times out counts
 * as not opened, as the collector recorded such pages.
 */
class PageInspector(
    private val context: Context,
    private val facts: PageFacts = PageFacts(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /** Tests add "data" to load inline pages without a network. */
    private val allowedSchemes: Set<String> = setOf("http", "https"),
    private val onObservation: (url: String, observation: PageObservation) -> Unit = { _, _ -> },
) : PageStage {
    private val mainHandler = Handler(Looper.getMainLooper())

    private sealed interface Outcome {
        data class Seen(val observation: PageObservation) : Outcome
        data class ScriptFailed(val detail: String) : Outcome
    }

    override suspend fun inspect(url: String, known: Evidence): PageResult {
        if (schemeOf(url) !in allowedSchemes) return PageResult.Unreadable(uncertain(ReasonCode.PAGE_UNSUPPORTED_SCHEME))
        val outcome = withTimeoutOrNull(timeoutMs) { withContext(Dispatchers.Main) { load(url) } }
            ?: Outcome.Seen(PageObservation(url = url, loaded = false))
        return when (outcome) {
            is Outcome.ScriptFailed -> PageResult.Unreadable(uncertain(ReasonCode.PAGE_SCRIPT_FAILED, outcome.detail))
            is Outcome.Seen -> {
                onObservation(url, outcome.observation)
                PageResult.Observed(facts.evidence(outcome.observation, known))
            }
        }
    }

    private suspend fun load(url: String): Outcome = suspendCancellableCoroutine { cont ->
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

        fun finish(outcome: Outcome) {
            if (finished) return
            release()
            if (cont.isActive) cont.resume(outcome)
        }

        fun failLoad() = finish(Outcome.Seen(PageObservation(url = url, loaded = false)))

        /*
         * Cancellation (e.g. the timeout) can arrive on any thread; WebView
         * must be torn down on the main thread.
         */
        cont.invokeOnCancellation { mainHandler.post { release() } }

        lockDown(webView)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (schemeOf(request.url.toString()) !in allowedSchemes) return true
                navigations++
                if (navigations > MAX_REDIRECTS) {
                    failLoad()
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, finishedUrl: String) {
                if (finished) return
                view.evaluateJavascript(PageSignals.inspectionScript) { rawResult ->
                    val outcome = runCatching {
                        val json = payload(rawResult) ?: return@runCatching Outcome.ScriptFailed("no data")
                        Outcome.Seen(PageSignals.observe(url, finishedUrl, httpStatus ?: 200, navigations, json))
                    }.getOrElse { Outcome.ScriptFailed(it.javaClass.simpleName) }
                    finish(outcome)
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
                if (request.isForMainFrame) failLoad()
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

    private fun uncertain(reason: ReasonCode, detail: String? = null) = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = Layer.PAGE_CONTENT,
        reason = reason,
        detail = detail,
    )

    private fun schemeOf(url: String): String? = url.trim().substringBefore(':', "").lowercase().ifEmpty { null }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val MAX_REDIRECTS = 5
    }
}
