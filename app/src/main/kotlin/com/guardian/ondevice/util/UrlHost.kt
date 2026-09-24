package com.guardian.ondevice.util

import java.net.URI

// Single source of truth for turning a URL string into a normalized,
// comparable host: lowercased and "www."-stripped, defaulting to https when
// the scheme is missing. Four call sites each had their own slightly
// different copy of this (some skipped lowercasing, some skipped the www
// strip) -- with no case normalization, an uppercase official-domain URL
// like "https://PAY.NAVER.COM" failed the official-domain check and then
// tripped brand-impersonation instead, since the allowlist compares against
// lowercase strings. Not used for model feature extraction: that needs
// java.net.URI's IDN limitation handled differently (see
// UrlFeatureExtractor.parse) and must stay byte-for-byte aligned with the
// Python training side's host parsing.
object UrlHost {
    fun of(raw: String?): String? {
        val trimmed = raw?.trim()
        if (trimmed.isNullOrEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = runCatching { URI(withScheme).host }.getOrNull() ?: return null
        return host.lowercase().removePrefix("www.").ifEmpty { null }
    }
}
