package com.guadian.ondevice.llm

/**
 * Runs the on-device model on one prompt. The llama.cpp binding implements this
 * outside `core/llm`, because it needs Android.
 */
fun interface LlmRuntime {
    /** The model's reply to [userMessage] (the system line is the runtime's to add), or null if no model is loaded. */
    suspend fun complete(userMessage: String): String?

    companion object {
        /** No model loaded: every call returns null. */
        val NONE = LlmRuntime { null }
    }
}
