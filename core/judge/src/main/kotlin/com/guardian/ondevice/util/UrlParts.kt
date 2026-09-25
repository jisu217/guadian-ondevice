package com.guardian.ondevice.util

/**
 * Lenient host/path/query split that never throws. Kept byte-for-byte aligned
 * with Python's urlparse for the model features and the rule parity fixture;
 * java.net.URI is avoided because it rejects IDN hosts and stray characters
 * that real ad URLs contain.
 */
data class UrlParts(val host: String, val path: String, val query: String) {
    val pathAndQuery: String get() = if (query.isEmpty()) path else "$path?$query"

    companion object {
        fun parse(url: String): UrlParts {
            val value = url.trim()
            val normalized = if ("://" in value) value else "https://$value"
            val afterScheme = normalized.substringAfter("://", normalized)
            val authority = afterScheme.substringBefore("/").substringBefore("?").substringBefore("#")
            val host = authority.substringAfterLast("@").substringBefore(":").lowercase()

            val afterAuthority = afterScheme.substring(minOf(authority.length, afterScheme.length))
            val pathAndQuery = afterAuthority.substringBefore("#")
            val path = pathAndQuery.substringBefore("?")
            val query = if ("?" in pathAndQuery) pathAndQuery.substringAfter("?") else ""
            return UrlParts(host = host, path = path, query = query)
        }
    }
}
