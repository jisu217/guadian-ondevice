@file:Suppress("DEPRECATION")

package com.guardian.ondevice.shield

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

/**
 * Covers AccessibilityNodeInfo logic with Robolectric instead of Android's
 * no-op local test stubs.
 */
@RunWith(RobolectricTestRunner::class)
class WebAccessibilityNodeRobolectricTest {

    @Before
    fun resetObtainedInstances() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances()
    }

    @Test
    fun isLinkIsTrueOnlyForLinkRole() {
        val link = chromeRoleNode(role = "link")
        val image = chromeRoleNode(role = "image")

        assertTrue(WebAccessibilityNode.isLink(link))
        assertFalse(WebAccessibilityNode.isLink(image))

        link.recycle()
        image.recycle()
    }

    @Test
    fun canBearUrlAcceptsLinkImageAndVideoRoles() {
        val link = chromeRoleNode(role = "link")
        val button = chromeRoleNode(role = "button")

        assertTrue(WebAccessibilityNode.canBearUrl(link))
        assertFalse(WebAccessibilityNode.canBearUrl(button))

        link.recycle()
        button.recycle()
    }

    @Test
    fun targetHostReadsAndParsesTheTargetUrlExtra() {
        val node = chromeRoleNode(role = "link", targetUrl = "https://m.example.com/promo?x=1")

        assertEquals("m.example.com", WebAccessibilityNode.targetHost(node))

        node.recycle()
    }

    @Test
    fun targetUrlKeepsThePathAndQuery() {
        // Shield judges the full URL so path/query rules (APK, lure keywords) apply.
        val node = chromeRoleNode(role = "link", targetUrl = "https://m.example.com/download/app.apk?x=1")

        assertEquals("https://m.example.com/download/app.apk?x=1", WebAccessibilityNode.targetUrl(node))

        node.recycle()
    }

    @Test
    fun targetHostIsNullWithoutATargetUrlExtra() {
        val node = chromeRoleNode(role = "link")

        assertNull(WebAccessibilityNode.targetHost(node))

        node.recycle()
    }

    @Test
    fun isNestedDocIsTrueForIframeRoles() {
        val iframe = chromeRoleNode(role = "iframe")
        val link = chromeRoleNode(role = "link")

        assertTrue(WebAccessibilityNode.isNestedDoc(iframe))
        assertFalse(WebAccessibilityNode.isNestedDoc(link))

        iframe.recycle()
        link.recycle()
    }

    @Test
    fun geckoRoleExtraIsReadTheSameWayAsChromeRole() {
        val node = AccessibilityNodeInfo.obtain()
        node.extras.putString("AccessibilityNodeInfo.geckoRole", "link")
        node.extras.putString("AccessibilityNodeInfo.targetUrl", "https://firefox-example.test/page")

        assertTrue(WebAccessibilityNode.isLink(node))
        assertEquals("firefox-example.test", WebAccessibilityNode.targetHost(node))

        node.recycle()
    }

    @Test
    fun isWebHostIsTrueForWebViewClassName() {
        val node = AccessibilityNodeInfo.obtain()
        node.className = "android.webkit.WebView"

        assertTrue(WebAccessibilityNode.isWebHost(node))

        node.recycle()
    }

    @Test
    fun isInsideNestedDocIsTrueWhenAnAncestorIsAnIframe() {
        val webHost = webHostNode()
        val iframe = chromeRoleNode(role = "iframe")
        val link = chromeRoleNode(role = "link", targetUrl = "https://ad-network.test/click")
        shadowOf(webHost).addChild(iframe)
        shadowOf(iframe).addChild(link)

        assertTrue(WebAccessibilityNode.isInsideNestedDoc(link))

        webHost.recycle()
        iframe.recycle()
        link.recycle()
    }

    @Test
    fun isInsideNestedDocIsFalseWhenTheAncestorChainReachesTheWebHostFirst() {
        val webHost = webHostNode()
        val paragraph = chromeRoleNode(role = "paragraph")
        val link = chromeRoleNode(role = "link", targetUrl = "https://m.example.com/about")
        shadowOf(webHost).addChild(paragraph)
        shadowOf(paragraph).addChild(link)

        assertFalse(WebAccessibilityNode.isInsideNestedDoc(link))

        webHost.recycle()
        paragraph.recycle()
        link.recycle()
    }

    @Test
    fun isInsideNestedDocIsFalseForAParentlessNode() {
        val link = chromeRoleNode(role = "link")

        assertFalse(WebAccessibilityNode.isInsideNestedDoc(link))

        link.recycle()
    }

    @Test
    fun inferCurrentSiteHostPicksTheMajorityLinkHost() {
        val root = webHostNode()
        val nav = chromeRoleNode(role = "rootWebArea")
        shadowOf(root).addChild(nav)
        val links = listOf(
            linkNode("http://10.0.2.2:8123/"),
            linkNode("http://10.0.2.2:8123/about"),
            linkNode("http://10.0.2.2:8123/contact"),
            linkNode("https://kakaopay-login-check.test/verify"),
        )
        links.forEach { shadowOf(nav).addChild(it) }

        assertEquals("10.0.2.2", WebAccessibilityNode.inferCurrentSiteHost(root))

        root.recycle()
        nav.recycle()
        links.forEach { it.recycle() }
    }

    @Test
    fun inferCurrentSiteHostIgnoresLinksInsideAnIframe() {
        val root = webHostNode()
        shadowOf(root).addChild(linkNode("http://10.0.2.2:8123/"))
        shadowOf(root).addChild(linkNode("http://10.0.2.2:8123/about"))
        val iframe = chromeRoleNode(role = "iframe")
        shadowOf(root).addChild(iframe)
        // A single ad link inside the iframe would otherwise win a naive
        // majority count if it were (wrongly) included.
        shadowOf(iframe).addChild(linkNode("https://ad-network.test/click"))

        assertEquals("10.0.2.2", WebAccessibilityNode.inferCurrentSiteHost(root))

        root.recycle()
        iframe.recycle()
    }

    @Test
    fun inferCurrentSiteHostIsNullWithNoLinks() {
        val root = webHostNode()

        assertNull(WebAccessibilityNode.inferCurrentSiteHost(root))

        root.recycle()
    }

    private fun webHostNode(): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        node.className = "android.webkit.WebView"
        return node
    }

    private fun linkNode(targetUrl: String): AccessibilityNodeInfo =
        chromeRoleNode(role = "link", targetUrl = targetUrl)

    private fun chromeRoleNode(role: String, targetUrl: String? = null): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        node.extras.putString("AccessibilityNodeInfo.chromeRole", role)
        if (targetUrl != null) {
            node.extras.putString("AccessibilityNodeInfo.targetUrl", targetUrl)
        }
        return node
    }
}
