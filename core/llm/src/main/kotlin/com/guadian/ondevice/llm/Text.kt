package com.guadian.ondevice.llm

import java.net.IDN
import java.util.regex.Pattern

/**
 * String helpers that match Python's semantics, so prompts built here equal the
 * ones built by model/llm/prompt_format.py character for character.
 */
internal object Text {
    // Python's \s is Unicode-aware; Java's is ASCII-only unless asked.
    private val WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS)

    /** `s[:n]` in Python: the first [n] code points, not UTF-16 units. */
    fun take(s: String, n: Int): String {
        if (s.codePointCount(0, s.length) <= n) return s
        return s.substring(0, s.offsetByCodePoints(0, n))
    }

    fun length(s: String): Int = s.codePointCount(0, s.length)

    /** `re.sub(r"\s+", "", s).lower()` */
    fun squash(s: String): String = WHITESPACE.matcher(s).replaceAll("").lowercase()

    /** `urlsplit(url).hostname`: lowercase host without user-info, port, or brackets. */
    fun hostname(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val netloc = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        val hostPort = netloc.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBefore(':')
        }
        return host.lowercase().ifEmpty { null }
    }

    /** `ascii_host` in prompt_format.py: the hostname in punycode. */
    fun asciiHost(url: String): String {
        val host = hostname(url) ?: return ""
        return runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
    }
}
