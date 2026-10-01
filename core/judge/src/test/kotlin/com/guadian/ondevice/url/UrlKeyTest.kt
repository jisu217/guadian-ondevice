package com.guadian.ondevice.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UrlKeyTest {
    @Test
    fun `normalizes host case, www, default port, fragment, and missing scheme`() {
        assertEquals("https://example.com/a?x=1", UrlKey.of("HTTPS://www.Example.COM:443/a?x=1#frag"))
        assertEquals("https://example.com/", UrlKey.of("example.com"))
    }

    @Test
    fun `drops click-tracking parameters but keeps the rest`() {
        assertEquals("https://example.com/a?id=7", UrlKey.of("https://example.com/a?utm_source=x&id=7&gclid=abc"))
    }

    @Test
    fun `keeps paths and non-default ports distinct`() {
        assertNotEquals(UrlKey.of("https://example.com/"), UrlKey.of("https://example.com/app.apk"))
        assertEquals("http://example.com:8080/", UrlKey.of("http://example.com:8080"))
    }

    @Test
    fun `falls back to the lowercased input when unparsable`() {
        assertEquals("not a url", UrlKey.of(" Not A URL "))
    }
}
