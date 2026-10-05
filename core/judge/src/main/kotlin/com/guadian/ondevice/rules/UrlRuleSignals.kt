package com.guadian.ondevice.rules

import com.guadian.ondevice.url.UrlParts

/**
 * Path/query signals for the URL stage. Checked against
 * `ml/fixtures/rules/rule_parity_cases.csv`, so a change here must be
 * mirrored there.
 */
object UrlRuleSignals {
    /*
     * ".apk" followed by a non-alphanumeric or the end, so "/app.apk?x" and
     * "file=a.apk" match but "/x.apkmirror" does not.
     */
    private val APK_PATTERN = Regex("""\.apk(?![a-z0-9])""")

    /** Only path and query: the URL's file, not its host. */
    fun target(url: String): String = UrlParts.parse(url).pathAndQuery.lowercase()

    fun hasApk(url: String): Boolean = APK_PATTERN.containsMatchIn(target(url))
}
