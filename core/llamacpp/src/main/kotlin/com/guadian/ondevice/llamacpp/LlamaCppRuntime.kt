package com.guadian.ondevice.llamacpp

import android.os.SystemClock
import android.util.Log
import com.guadian.ondevice.llm.ChatFormat
import com.guadian.ondevice.llm.LlmRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [LlmRuntime] backed by llama.cpp on the CPU.
 *
 * The model loads on first use and stays loaded. A missing or unloadable file,
 * or a CPU without the dot-product instructions the native library is built for,
 * makes every call return null, which the LLM stage reports as "not loaded"; a
 * failed load is not retried until the app restarts. Replies are greedy
 * (temperature 0), as in training evaluation.
 */
class LlamaCppRuntime(
    private val modelFile: File,
    private val threads: Int = DEFAULT_THREADS,
    private val contextTokens: Int = CONTEXT_TOKENS,
    private val maxNewTokens: Int = MAX_NEW_TOKENS,
) : LlmRuntime {
    private val lock = Mutex()
    private var handle = 0L
    private var loadFailed = false

    override suspend fun complete(userMessage: String): String? = withContext(Dispatchers.Default) {
        lock.withLock {
            val session = session() ?: return@withLock null
            val started = SystemClock.elapsedRealtime()
            val reply = LlamaCpp.nativeComplete(session, ChatFormat.render(userMessage).toByteArray(), maxNewTokens)
                ?.toString(Charsets.UTF_8)
            Log.d(TAG, "reply in ${SystemClock.elapsedRealtime() - started}ms: $reply")
            reply
        }
    }

    private fun session(): Long? {
        if (handle != 0L) return handle
        if (loadFailed || !modelFile.isFile || !CpuFeatures.hasDotProduct) return null
        val started = SystemClock.elapsedRealtime()
        handle = LlamaCpp.nativeLoad(modelFile.path, contextTokens, threads)
        if (handle == 0L) {
            loadFailed = true
            Log.w(TAG, "could not load ${modelFile.path}")
            return null
        }
        Log.i(TAG, "loaded ${modelFile.name} in ${SystemClock.elapsedRealtime() - started}ms")
        return handle
    }

    companion object {
        private const val TAG = "GuadianLlama"

        // The longest training prompt is about 700 tokens; the answer is at most 96 (notebook MAX_NEW_TOKENS).
        const val CONTEXT_TOKENS = 1024
        const val MAX_NEW_TOKENS = 96

        // Big cores on most phones; more threads than big cores slows llama.cpp down.
        val DEFAULT_THREADS = minOf(4, Runtime.getRuntime().availableProcessors())
    }
}
