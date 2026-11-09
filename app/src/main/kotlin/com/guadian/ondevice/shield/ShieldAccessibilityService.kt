package com.guadian.ondevice.shield

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.guadian.ondevice.GuadianApp
import com.guadian.ondevice.judge.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * System-wide browser-click trigger for the shared judge graph.
 *
 * The user must enable this service under Android accessibility settings. A
 * candidate click is an off-site link or an iframe link; it is not a confirmed
 * ad-slot classification.
 *
 * The service judges each candidate and writes the verdict to Logcat under
 * [LOG_TAG]. It does not intervene: no overlay is shown and the navigation is
 * left alone, so the page the user opened is never affected.
 */
class ShieldAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var judging: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val node = event.source ?: return
        if (!WebAccessibilityNode.isLink(node) || !WebAccessibilityNode.canBearUrl(node)) return
        val targetUrl = WebAccessibilityNode.targetUrl(node) ?: return
        val targetHost = WebAccessibilityNode.hostOf(targetUrl) ?: return

        /*
         * Iframe clicks are candidates by themselves; other clicks must leave
         * the site inferred from the page's own top-level links.
         */
        if (!WebAccessibilityNode.isInsideNestedDoc(node)) {
            val root = rootInActiveWindow ?: return
            val pageHost = WebAccessibilityNode.inferCurrentSiteHost(root) ?: return
            if (WebAccessibilityNode.sameSite(targetHost, pageHost)) return
        }

        judgeClick(targetUrl)
    }

    override fun onInterrupt() {
        judging?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun judgeClick(url: String) {
        // Only the newest click is judged; an older one still running is dropped.
        judging?.cancel()
        val app = application as GuadianApp
        judging = scope.launch {
            val started = SystemClock.elapsedRealtime()
            val verdict = withTimeoutOrNull(JUDGE_BUDGET_MS) {
                withContext(Dispatchers.Default) { app.judges.shield.judge(url) }
            }
            Log.i(LOG_TAG, describe(url, verdict, SystemClock.elapsedRealtime() - started))
        }
    }

    private fun describe(url: String, verdict: Verdict?, elapsedMs: Long): String =
        if (verdict == null) {
            "timeout after ${elapsedMs}ms url=$url"
        } else {
            "${verdict.risk} ${verdict.confidence} layer=${verdict.layer.wireName} " +
                "reason=${verdict.reason} category=${verdict.category.wireName} " +
                "${elapsedMs}ms url=$url"
        }

    private companion object {
        const val LOG_TAG = "GuadianShield"

        // Bounds the work per click; a slower judgment is logged as a timeout.
        const val JUDGE_BUDGET_MS = 3_000L
    }
}
