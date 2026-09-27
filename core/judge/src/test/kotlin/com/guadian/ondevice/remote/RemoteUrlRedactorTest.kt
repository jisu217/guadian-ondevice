package com.guadian.ondevice.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteUrlRedactorTest {
    @Test
    fun `masks query values but keeps keys`() {
        assertEquals(
            "https://example.com/p?session=_&id=_&flag",
            RemoteUrlRedactor.redact("https://example.com/p?session=abc123&id=42&flag"),
        )
    }

    @Test
    fun `keeps nested destination urls, encoded or not`() {
        val url = "https://ad.example/click?adurl=https%3A%2F%2Fland.example%2Fx&gclid=abc"

        assertEquals("https://ad.example/click?adurl=https%3A%2F%2Fland.example%2Fx&gclid=_", RemoteUrlRedactor.redact(url))
    }

    @Test
    fun `drops user info and fragment`() {
        assertEquals("https://example.com/a", RemoteUrlRedactor.redact("https://me:pw@example.com/a#top"))
    }

    @Test
    fun `leaves urls without a query unchanged`() {
        assertEquals("https://example.com/download/app.apk", RemoteUrlRedactor.redact("https://example.com/download/app.apk"))
    }
}
