package com.guardian.ondevice.util

import java.net.URI

/**
 * Normalizes a URL into a cache key: scheme and host lowercased, `www.`
 * and default ports dropped, fragment and user-info removed, and
 * click-tracking parameters stripped. Path and remaining query are kept
 * as-is, because two paths on one host can carry very different risk
 * (`/` vs `/app.apk`).
 */
object UrlKey {
    private val TRACKING_KEYS = setOf(
        "gclid", "dclid", "fbclid", "msclkid", "gad_source", "gad_campaignid",
    )

    fun of(raw: String): String {
        val trimmed = raw.trim()
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.")
        if (uri == null || host.isNullOrEmpty()) return trimmed.lowercase()

        val scheme = uri.scheme.lowercase()
        val port = uri.port.takeUnless { it == -1 || it == defaultPort(scheme) }
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery
            ?.split('&')
            ?.filter { it.isNotEmpty() && !isTracking(it.substringBefore('=')) }
            ?.joinToString("&")
            .orEmpty()

        return buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(path)
            if (query.isNotEmpty()) append('?').append(query)
        }
    }

    private fun isTracking(key: String): Boolean {
        val lowered = key.lowercase()
        return lowered.startsWith("utm_") || lowered in TRACKING_KEYS
    }

    private fun defaultPort(scheme: String): Int = when (scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
}
