package com.guardian.ondevice.judge

enum class Risk {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN,
}

enum class Confidence {
    CERTAIN,
    UNCERTAIN,
}

data class Verdict(
    val risk: Risk,
    val reason: String,
    val advice: String,
    val source: String,
    val confidence: Confidence = Confidence.CERTAIN,
    val site: String? = null,
    val type: String = "none",
)

