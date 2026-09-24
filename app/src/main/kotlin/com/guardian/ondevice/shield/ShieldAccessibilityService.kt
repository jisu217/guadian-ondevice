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
import com.guardian.ondevice.judge.DefaultJudgeGraph
import com.guardian.ondevice.judge.HybridJudge
import com.guardian.ondevice.judge.Verdict

/**
 * System-wide browser-click trigger for the same HybridJudge pipeline used by
 * JudgeActivity.
 *
 * The user must enable this service under Android accessibility settings. A
 * candidate click is an off-site link or an iframe link; it is not a confirmed
 * ad-slot classification.
 */
class ShieldAccessibilityService : AccessibilityService() {

    private val mainThread = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    private val hybridJudge: HybridJudge by lazy {
        DefaultJudgeGraph.create(context = this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val node = event.source ?: return
        if (!WebAccessibilityNode.isLink(node) || !WebAccessibilityNode.canBearUrl(node)) return
        val targetHost = WebAccessibilityNode.targetHost(node) ?: return

        // Iframe clicks are candidates by themselves; other clicks must leave
        // the site inferred from the page's own top-level links.
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

    // Bound accessibility services can use this without SYSTEM_ALERT_WINDOW.
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
