package com.guadian.ondevice.llamacpp

/** The native bridge in llama_jni.cpp. Not thread-safe: callers serialize access per handle. */
internal object LlamaCpp {
    init {
        System.loadLibrary("guadian_llama")
    }

    /** A session handle, or 0 if the model or its context could not be created. */
    @JvmStatic external fun nativeLoad(path: String, contextTokens: Int, threads: Int): Long

    /** The reply as UTF-8, or null if the prompt did not fit or decoding failed. */
    @JvmStatic external fun nativeComplete(handle: Long, promptUtf8: ByteArray, maxTokens: Int): ByteArray?

    @JvmStatic external fun nativeFree(handle: Long)
}
