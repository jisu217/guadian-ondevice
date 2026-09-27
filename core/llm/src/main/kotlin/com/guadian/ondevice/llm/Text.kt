package com.guadian.ondevice.llm

import java.net.IDN
import java.util.regex.Pattern

/**
 * String helpers that match Python's semantics, so prompts built here equal the
 * ones built by model/llm/prompt_format.py character for character.
 */
internal object Text {
    /**
     * Python's `\s` in a str pattern: every character for which `str.isspace()` is true.
     * Spelled out because Java's `\s` is ASCII-only, and the flag that widens it
     * (UNICODE_CHARACTER_CLASS) is unsupported on Android.
     */
    const val PY_WHITESPACE = "[\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]"

    /** Python's `\w` for these patterns: letters, digits, underscore, in any script. */
    const val PY_WORD = "[\\p{L}\\p{N}_]"

    private val WHITESPACE = Pattern.compile("$PY_WHITESPACE+")

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
