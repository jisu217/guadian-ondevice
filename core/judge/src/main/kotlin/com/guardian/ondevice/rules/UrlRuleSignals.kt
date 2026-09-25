package com.guardian.ondevice.rules

import com.guardian.ondevice.util.UrlParts

/**
 * Path/query signals shared with server/judge_service.py's local precheck.
 * Both sides are checked against `model/data/fixtures/rule_parity_cases.csv`,
 * so a change here must be mirrored there.
 */
internal object UrlRuleSignals {
    val SUSPICIOUS_TOKENS = setOf("free", "event", "gift", "loan", "verify", "account")

    // ".apk" followed by a non-alphanumeric or the end, so "/app.apk?x" and
    // "file=a.apk" match but "/x.apkmirror" does not.
    private val APK_PATTERN = Regex("""\.apk(?![a-z0-9])""")
    private val TOKEN_SPLIT = Regex("[^a-z0-9]+")

    /** Only path and query: a host like "myaccount.google.com" is not a lure. */
    fun target(url: String): String = UrlParts.parse(url).pathAndQuery.lowercase()

    fun hasApk(url: String): Boolean = APK_PATTERN.containsMatchIn(target(url))

    fun hasSuspiciousToken(url: String): Boolean =
        target(url).split(TOKEN_SPLIT).any { it in SUSPICIOUS_TOKENS }
}
