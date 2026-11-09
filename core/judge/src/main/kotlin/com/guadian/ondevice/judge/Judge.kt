package com.guadian.ondevice.judge

/**
 * One stage of the judgment pipeline. Implementations choose their own
 * dispatcher (e.g. PageContentJudge moves to Main for its WebView), so
 * callers may invoke this from any coroutine context.
 */
interface Judge {
    suspend fun judge(url: String): Verdict
}
