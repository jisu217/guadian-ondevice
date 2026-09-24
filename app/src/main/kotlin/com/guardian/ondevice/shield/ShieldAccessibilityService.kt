package com.guardian.ondevice.shield

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.guardian.ondevice.R
import com.guardian.ondevice.judge.HybridJudge
import com.guardian.ondevice.judge.LocalReasoningJudge
import com.guardian.ondevice.judge.RemoteJudge
import com.guardian.ondevice.judge.RuleBasedJudge
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.model.OnDeviceModelJudge
import com.guardian.ondevice.rag.LocalRagJudge
import com.guardian.ondevice.rules.ApkRule
import com.guardian.ondevice.rules.BrandImpersonationRule
import com.guardian.ondevice.rules.SuspiciousUrlRule
import com.guardian.ondevice.store.LocalRiskStore
import com.guardian.ondevice.webview.PageContentJudge

// Watches known browser apps' accessibility trees for a link click that
// leaves the current site (or one inside an iframe, a strong ad-slot signal
// on its own), then judges the destination with the same HybridJudge
// pipeline JudgeActivity uses (rule/model/RAG/page-content/remote), behind a
// full-screen overlay while the verdict is pending. This is the system-wide
// counterpart to JudgeActivity's manual URL entry: same judgment pipeline,
// different trigger.
//
// The user must enable this service by hand under Settings > Accessibility --
// Android has no runtime-permission dialog for accessibility access.
//
// "Ad click" here is approximated as "link click leaving the current site, or
// inside an iframe" (see WebAccessibilityNode); it is not restricted to
// actual ad networks, since that would need an ad-host denylist this project
// does not maintain. HybridJudge still resolves an ordinary outbound link as
// LOW risk quickly, so the cost of the broader heuristic is a few extra local
// judge calls, not wrong verdicts.
class ShieldAccessibilityService : AccessibilityService() {

    private val mainThread = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    private val hybridJudge: HybridJudge by lazy {
        HybridJudge(
            store = LocalRiskStore(),
            ruleBasedJudge = RuleBasedJudge(
                rules = listOf(ApkRule(), BrandImpersonationRule(), SuspiciousUrlRule())
            ),
            localReasoningJudge = LocalReasoningJudge(
                judges = listOf(
                    OnDeviceModelJudge(),
                    LocalRagJudge(),
                    PageContentJudge(context = this),
                )
            ),
            remoteJudge = RemoteJudge(),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val node = event.source ?: return
        if (!WebAccessibilityNode.isLink(node) || !WebAccessibilityNode.canBearUrl(node)) return
        val targetHost = WebAccessibilityNode.targetHost(node) ?: return

        // A click inside an iframe is a strong ad-slot signal on its own,
        // even if the destination happens to resolve to the page's own site
        // (an ad iframe pointing at a same-site redirector). Otherwise, fall
        // back to "does this leave the current site" -- inferred from the
        // page's own top-level links (see WebAccessibilityNode.
        // inferCurrentSiteHost), not the browser's address bar, so this works
        // the same way across any Chromium- or Gecko-based browser.
        if (!WebAccessibilityNode.isInsideNestedDoc(node)) {
            val root = rootInActiveWindow ?: return
            val pageHost = WebAccessibilityNode.inferCurrentSiteHost(root) ?: return
            if (WebAccessibilityNode.sameSite(targetHost, pageHost)) return
        }

        judgeAndShow("https://$targetHost")
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    private fun judgeAndShow(url: String) {
        mainThread.post { showOverlay(verdict = null) }
        hybridJudge.judge(url) { verdict ->
            mainThread.post { showOverlay(verdict) }
        }
    }

    private fun showOverlay(verdict: Verdict?) {
        val view = overlayView ?: inflateOverlay().also {
            overlayView = it
            addOverlayToWindow(it)
        }

        view.findViewById<TextView>(R.id.shieldStatusText).text = if (verdict == null) {
            getString(R.string.shield_pending)
        } else {
            listOf(
                "${getString(R.string.shield_risk_label)}: ${verdict.risk}",
                "${getString(R.string.shield_reason_label)}: ${verdict.reason}",
                "${getString(R.string.shield_advice_label)}: ${verdict.advice}",
            ).joinToString("\n")
        }
        view.findViewById<Button>(R.id.shieldDismissButton).setOnClickListener { removeOverlay() }
    }

    private fun inflateOverlay(): View {
        return LayoutInflater.from(this).inflate(R.layout.shield_overlay, null)
    }

    // TYPE_ACCESSIBILITY_OVERLAY is available to a bound AccessibilityService
    // without the separate SYSTEM_ALERT_WINDOW permission a regular Activity
    // would need for a draw-over-other-apps window.
    private fun addOverlayToWindow(view: View) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(view, params)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    private val windowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager
}
