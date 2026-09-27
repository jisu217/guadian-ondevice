package com.guadian.ondevice.judge

/**
 * One stage of the judgment pipeline. Implementations choose their own
 * dispatcher (e.g. RemoteJudge moves to IO, PageContentJudge to Main), so
 * callers may invoke this from any coroutine context.
 */
interface Judge {
    suspend fun judge(url: String): Verdict
}
