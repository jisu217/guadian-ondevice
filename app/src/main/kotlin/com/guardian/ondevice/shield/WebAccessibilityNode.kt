package com.guardian.ondevice.shield

import android.view.accessibility.AccessibilityNodeInfo
import java.net.URI

// Node-reading helpers adapted from reference/WebNode.kt (참고 출처:
// SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/WebNode.kt),
// trimmed to what ShieldAccessibilityService needs: recognizing a link click
// inside rendered web content, resolving its destination host, and inferring
// which site the current page itself belongs to. The original's ad-network
// denylist (`AdRules`) and click-accounting-safe redirect handling
// (`advertiserSiteOf`/`earlyJudgeTarget`) are not ported -- this version does
// not try to identify "is this specifically an ad", only "did the user click
// a link that leaves the current site" (or one inside an iframe, a strong
// ad-slot signal on its own), which HybridJudge (rule/model/RAG/page-content/
// remote) then judges regardless of whether the click came from an ad slot or
// ordinary page content.
object WebAccessibilityNode {

    private const val CHROME_ROLE = "AccessibilityNodeInfo.chromeRole"
    private const val GECKO_ROLE = "AccessibilityNodeInfo.geckoRole"
    private const val TARGET_URL = "AccessibilityNodeInfo.targetUrl"
    private const val WEB_HOST_CLASS = "android.webkit.WebView"

    // Roles the browser fills targetUrl for (links, images, video).
    private val urlBearingRoles = setOf("link", "image", "video", "img")

    // Roles marking the boundary of a nested document (iframe). Content past
    // this boundary belongs to whoever embedded the iframe, not the page.
    private val nestedDocRoles = setOf("iframe", "iframePresentational")

    // Bound the cost of inferCurrentSiteHost's tree walk on a large page.
    private const val MAX_NODES_TO_SCAN = 500
    private const val MAX_ANCESTOR_HOPS = 20

    // Two-level public suffixes (e.g. "co.kr"): registrable domain needs three
    // labels here, not two, or "m.news.nate.com" and "m.nate.com" look like
    // different sites. Incomplete on purpose -- wrong only means an internal
    // link is misread as leaving the site (a missed click), never the reverse.
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

    // True once we're inside a browser's rendered web document, as opposed to
    // the browser's own native chrome (address bar, tab strip, menu).
    fun isWebHost(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString() == WEB_HOST_CLASS) return true
        return role(node) == "rootWebArea"
    }

    fun canBearUrl(node: AccessibilityNodeInfo): Boolean {
        val r = role(node) ?: return false
        return r.lowercase() in urlBearingRoles
    }

    // Anchors only, not image src -- an <img> inside a link points at a CDN,
    // not the destination site.
    fun isLink(node: AccessibilityNodeInfo): Boolean = role(node)?.lowercase() == "link"

    fun targetHost(node: AccessibilityNodeInfo): String? =
        str(node, TARGET_URL)?.let { hostOf(it) }

    /** True for the node marking the boundary into an iframe's own document. */
    fun isNestedDoc(node: AccessibilityNodeInfo): Boolean = role(node) in nestedDocRoles

    /**
     * True if [node] sits inside an iframe -- walks ancestors until it finds
     * a nested-doc boundary (inside an ad slot) or the page's own web host
     * (not inside any iframe). A click here is a strong ad-slot signal on its
     * own, independent of whether the destination host happens to match the
     * page's site (an ad iframe can point to a same-site redirector).
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
     * Infers which site the current page belongs to, without reading the
     * browser's own address bar. The address bar text is unreliable even
     * within Chrome alone -- it drops out of the accessibility tree entirely
     * once the toolbar scrolls out of view -- so instead this counts the
     * hosts linked to by the page's own top-level (non-iframe) links and
     * takes the most common one, mirroring the original GUADIAN's `AdScanner`
     * approach. Because it only reads the same chromeRole/geckoRole extras
     * `targetHost` already uses, it works the same way for any Chromium- or
     * Gecko-based browser, unlike an address-bar-resource-id lookup that
     * would need updating per browser.
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

    // java.net.URI rather than android.net.Uri: a real JVM class, so this is
    // unit-testable locally instead of needing a device/emulator, and it
    // matches how BrandImpersonationRule already parses hosts elsewhere.
    fun hostOf(raw: String?): String? {
        var s = raw?.trim() ?: return null
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        val host = try {
            URI(s).host?.lowercase()
        } catch (e: Throwable) {
            null
        } ?: return null
        return host.ifEmpty { null }
    }

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
