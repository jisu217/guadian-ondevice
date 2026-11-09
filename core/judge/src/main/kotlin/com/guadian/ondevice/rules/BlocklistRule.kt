package com.guadian.ondevice.rules

import com.guadian.ondevice.url.PublicSuffixList
import com.guadian.ondevice.url.UrlHost

/**
 * Whether the host or one of its parent domains is on the bundled blocklist: a URL
 * signal for the verdict table, which decides what it means.
 *
 * The walk stops at the registrable domain, so a listed `evil.example` also
 * catches `login.evil.example`, but nothing above it (such as a shared hosting
 * suffix) is ever consulted.
 */
class BlocklistRule(
    private val filter: BlocklistFilter = BlocklistFilter.default,
    private val suffixes: PublicSuffixList = PublicSuffixList.default,
) {
    /** The listed domain the URL's host falls under, or null. */
    fun listedDomain(url: String): String? {
        val host = UrlHost.of(url) ?: return null
        val registrable = suffixes.registrableDomain(host) ?: return null
        return generateSequence(host) { it.substringAfter('.', "").ifEmpty { null } }
            .takeWhile { it.length >= registrable.length }
            .firstOrNull(filter::mightContain)
    }
}
