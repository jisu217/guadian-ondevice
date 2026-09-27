package com.guadian.ondevice.remote

import java.net.URLDecoder

/**
 * Trims what leaves the device: user-info and fragment are dropped, and query
 * values are replaced with "_" unless they are nested destination URLs (the
 * part of an ad redirect the remote judge actually needs). Keys are kept, so
 * the server still sees tracking/redirect structure. Paths are kept as-is.
 */
object RemoteUrlRedactor {
    private const val MASK = "_"

    fun redact(url: String): String {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        val scheme = if (schemeEnd >= 0) trimmed.substring(0, schemeEnd + 3) else ""
        val rest = trimmed.substring(scheme.length).substringBefore('#')

        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd).substringAfterLast('@')
        val pathAndQuery = rest.substring(authorityEnd)
        val path = pathAndQuery.substringBefore('?')
        val query = if ('?' in pathAndQuery) pathAndQuery.substringAfter('?') else null

        val redactedQuery = query?.split('&')?.joinToString("&") { pair ->
            if ('=' !in pair) return@joinToString pair
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=')
            if (looksLikeUrl(value)) pair else "$key=$MASK"
        }

        return buildString {
            append(scheme).append(authority).append(path)
            if (redactedQuery != null) append('?').append(redactedQuery)
        }
    }

    private fun looksLikeUrl(value: String): Boolean {
        val decoded = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
        val lowered = decoded.lowercase()
        return lowered.startsWith("http://") || lowered.startsWith("https://")
    }
}
