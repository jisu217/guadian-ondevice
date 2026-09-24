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
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

// Covers the single-node classification functions that need a real
// AccessibilityNodeInfo (role/isLink/canBearUrl/targetHost/isNestedDoc): the
// local `test` sourceSet's default stub android.jar no-ops these, but
// Robolectric gives AccessibilityNodeInfo (and the Bundle its extras are
// stored in) a real, working implementation, so these run as ordinary JVM
// tests instead of only being verifiable by hand on an emulator.
//
// Tree-walking functions (inferCurrentSiteHost, isInsideNestedDoc's ancestor
// walk) are not covered here -- they need a real View-backed node hierarchy
// with working parent/child navigation, which is a materially harder fake to
// build correctly than a single node's own properties. Those remain covered
// only by the manual emulator verification in docs/architecture.md.
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

    private fun chromeRoleNode(role: String, targetUrl: String? = null): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        node.extras.putString("AccessibilityNodeInfo.chromeRole", role)
        if (targetUrl != null) {
            node.extras.putString("AccessibilityNodeInfo.targetUrl", targetUrl)
        }
        return node
    }
}
