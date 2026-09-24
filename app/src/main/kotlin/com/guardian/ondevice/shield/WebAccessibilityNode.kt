package com.guardian.ondevice.shield

import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.ondevice.util.UrlHost

/**
 * Browser accessibility-tree helpers used by ShieldAccessibilityService.
 *
 * Adapted from `reference/WebNode.kt`
 * (`SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/WebNode.kt`)
 * and reduced to link detection, target-host extraction, iframe detection, and
 * current-site inference. The original ad-network denylist and redirect
 * accounting helpers are intentionally not ported; this version detects
 * off-site or iframe link clicks and lets HybridJudge decide the risk.
 */
object WebAccessibilityNode {

    private const val CHROME_ROLE = "AccessibilityNodeInfo.chromeRole"
    private const val GECKO_ROLE = "AccessibilityNodeInfo.geckoRole"
    private const val TARGET_URL = "AccessibilityNodeInfo.targetUrl"
    private const val WEB_HOST_CLASS = "android.webkit.WebView"

    // Browser roles that can carry targetUrl.
    private val urlBearingRoles = setOf("link", "image", "video", "img")

    // Nested-document boundaries mark iframe content.
    private val nestedDocRoles = setOf("iframe", "iframePresentational")

    private const val MAX_NODES_TO_SCAN = 500
    private const val MAX_ANCESTOR_HOPS = 20

    // Minimal two-level suffix support for siteOf("m.news.nate.com") == "nate.com".
    private val twoLevelSuffixes = setOf(
        "co", "or", "ne", "go", "re", "pe", "ac", "hs", "ms", "es", "sc", "kg", "com", "net", "org"
    )

    private fun str(node: AccessibilityNodeInfo, key: String): String? = try {
        node.extras?.getString(key)?.takeIf { it.isNotEmpty() }
    } catch (e: Throwable) {
        null
    }

    private fun role(node: AccessibilityNodeInfo): String? =
        str(node, CHROME_ROLE) ?: str(node, GECKO_ROLE)

    /** True for rendered web content rather than browser chrome. */
    fun isWebHost(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString() == WEB_HOST_CLASS) return true
        return role(node) == "rootWebArea"
    }

    fun canBearUrl(node: AccessibilityNodeInfo): Boolean {
        val r = role(node) ?: return false
        return r.lowercase() in urlBearingRoles
    }

    /** Anchors only; image roles often point to CDN assets, not navigation targets. */
    fun isLink(node: AccessibilityNodeInfo): Boolean = role(node)?.lowercase() == "link"

    fun targetHost(node: AccessibilityNodeInfo): String? =
        str(node, TARGET_URL)?.let { hostOf(it) }

    /** True for the node marking the boundary into an iframe's own document. */
    fun isNestedDoc(node: AccessibilityNodeInfo): Boolean = role(node) in nestedDocRoles

    /**
     * True if [node] sits inside an iframe. A nested document is treated as a
     * candidate even when the destination host later resolves to the same site.
     */
    fun isInsideNestedDoc(node: AccessibilityNodeInfo): Boolean {
        var current = node.parent
        var hops = 0
        while (current != null && hops < MAX_ANCESTOR_HOPS) {
            if (isNestedDoc(current)) return true
            if (isWebHost(current)) return false
            current = current.parent
            hops++
        }
        return false
    }

    /**
     * Infers the page's site from top-level links instead of the browser
     * address bar, whose accessibility node is browser-specific and unstable.
     */
    fun inferCurrentSiteHost(root: AccessibilityNodeInfo): String? {
        val hostCounts = mutableMapOf<String, Int>()
        var scanned = 0

        fun walk(node: AccessibilityNodeInfo) {
            if (scanned >= MAX_NODES_TO_SCAN) return
            scanned++
            if (isLink(node)) {
                targetHost(node)?.let { host -> hostCounts[host] = (hostCounts[host] ?: 0) + 1 }
            }
            for (i in 0 until node.childCount) {
                if (scanned >= MAX_NODES_TO_SCAN) return
                val child = node.getChild(i) ?: continue
                if (isNestedDoc(child)) continue // don't descend into iframes
                walk(child)
            }
        }

        walk(root)
        return hostCounts.maxByOrNull { it.value }?.key
    }

    fun hostOf(raw: String?): String? = UrlHost.of(raw)

    fun siteOf(host: String): String {
        val parts = host.lowercase().trim('.').split('.')
        if (parts.size <= 2) return parts.joinToString(".")
        val secondLast = parts[parts.size - 2]
        val take = if (parts.last().length <= 3 && secondLast in twoLevelSuffixes) 3 else 2
        return parts.takeLast(minOf(take, parts.size)).joinToString(".")
    }

    fun sameSite(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return siteOf(a) == siteOf(b)
    }
}
