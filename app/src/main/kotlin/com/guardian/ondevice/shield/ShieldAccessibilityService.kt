package com.guardian.ondevice.shield

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.guardian.ondevice.GuardianApp
import com.guardian.ondevice.R
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.ui.VerdictText
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
 * A click event arrives after the browser has already started navigating, so
 * this service cannot block the navigation. It reacts instead: HIGH goes back
 * and explains why, MEDIUM shows a warning, LOW and UNKNOWN stay silent.
 */
class ShieldAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var judging: Job? = null
    private var bannerView: View? = null
    private var warningView: View? = null
    private val text by lazy { VerdictText(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val node = event.source ?: return
        if (!WebAccessibilityNode.isLink(node) || !WebAccessibilityNode.canBearUrl(node)) return
        val targetUrl = WebAccessibilityNode.targetUrl(node) ?: return
        val targetHost = WebAccessibilityNode.hostOf(targetUrl) ?: return

        // Iframe clicks are candidates by themselves; other clicks must leave
        // the site inferred from the page's own top-level links.
        if (!WebAccessibilityNode.isInsideNestedDoc(node)) {
            val root = rootInActiveWindow ?: return
            val pageHost = WebAccessibilityNode.inferCurrentSiteHost(root) ?: return
            if (WebAccessibilityNode.sameSite(targetHost, pageHost)) return
        }

        judgeClick(targetUrl)
    }

    override fun onInterrupt() {
        judging?.cancel()
        removeBanner()
        removeWarning()
    }

    override fun onDestroy() {
        scope.cancel()
        removeBanner()
        removeWarning()
        super.onDestroy()
    }

    private fun judgeClick(url: String) {
        // Only the newest click matters; cancelling also stops a stale result
        // from reopening a warning the user already dismissed.
        judging?.cancel()
        showBanner()
        val app = application as GuardianApp
        judging = scope.launch {
            try {
                val verdict = withTimeoutOrNull(JUDGE_BUDGET_MS) {
                    withContext(Dispatchers.Default) { app.judges.shield.judge(url) }
                }
                react(verdict)
            } finally {
                removeBanner()
            }
        }
    }

    private fun react(verdict: Verdict?) {
        when (verdict?.risk) {
            Risk.HIGH -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                showWarning(verdict, wentBack = true)
            }
            Risk.MEDIUM -> showWarning(verdict, wentBack = false)
            Risk.LOW, Risk.UNKNOWN, null -> Unit
        }
    }

    private fun showBanner() {
        if (bannerView != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.shield_banner, null)
        view.findViewById<TextView>(R.id.shieldBannerText).text = getString(R.string.shield_pending)
        // Not focusable and not touchable: the page stays fully usable meanwhile.
        windowManager.addView(
            view,
            overlayParams(
                height = WindowManager.LayoutParams.WRAP_CONTENT,
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            ),
        )
        bannerView = view
    }

    private fun showWarning(verdict: Verdict, wentBack: Boolean) {
        val view = warningView ?: LayoutInflater.from(this).inflate(R.layout.shield_overlay, null).also {
            windowManager.addView(it, overlayParams(height = WindowManager.LayoutParams.MATCH_PARENT, flags = 0))
            warningView = it
        }
        val advice = if (wentBack) getString(R.string.advice_shield_went_back) else text.advice(verdict.risk)
        view.findViewById<TextView>(R.id.shieldStatusText).text = listOf(
            "${getString(R.string.label_risk)}: ${text.risk(verdict.risk)}",
            "${getString(R.string.label_reason)}: ${text.reason(verdict)}",
            "${getString(R.string.label_advice)}: $advice",
        ).joinToString("\n")
        view.findViewById<Button>(R.id.shieldDismissButton).setOnClickListener { removeWarning() }
    }

    // Bound accessibility services can use this without SYSTEM_ALERT_WINDOW.
    private fun overlayParams(height: Int, flags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun removeBanner() {
        bannerView?.let { windowManager.removeView(it) }
        bannerView = null
    }

    private fun removeWarning() {
        warningView?.let { windowManager.removeView(it) }
        warningView = null
    }

    private val windowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager

    private companion object {
        // A click is already navigating; a verdict later than this is no longer useful.
        const val JUDGE_BUDGET_MS = 3_000L
    }
}
