package com.guadian.ondevice.judge

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

/** Pipeline stage that produced a verdict (docs/design.md, section 3). */
enum class Layer(val wireName: String) {
    STORE("local-store"),
    URL("url"),
    PAGE_CONTENT("page-content"),
    LLM("llm"),
}

/**
 * What kind of risk a verdict describes. [wireName] is the snake_case name
 * used in fixtures and anywhere a category is read from text.
 */
enum class RiskCategory(val wireName: String) {
    NONE("none"),
    BLOCKLISTED("blocklisted"),
    APK("apk"),
    BRAND_IMPERSONATION("brand_impersonation"),
    PERSONAL_INFO("personal_info"),
    URGENCY("urgency"),
    CREDENTIALS("credentials"),
    PAYMENT("payment"),
    INVESTMENT("investment"),
    CONTENT_FARM("contentfarm"),
    UNVERIFIABLE("unverifiable"),

    /** A category name this app version does not know. */
    OTHER("other");

    companion object {
        fun fromWire(value: String?): RiskCategory =
            if (value.isNullOrEmpty()) NONE else entries.firstOrNull { it.wireName == value } ?: OTHER
    }
}

/**
 * Why a verdict was reached. The UI turns this into user-facing text, so
 * judges carry no display strings; [Verdict.detail] holds the variable part.
 */
enum class ReasonCode {
    // Verdict table decisions (VerdictPipeline): which row decided.
    BLOCKLISTED,
    OFFICIAL_SITE,
    IMPERSONATION,
    SUSPICIOUS_HOST,
    PAGE_UNVERIFIABLE,
    SIGN_IN_SCREEN,
    PERSONAL_FIELD,
    SENSITIVE_FIELD,
    APK_DOWNLOAD,
    PAYMENT_PUSH,
    INVESTMENT_OFFER,
    REWARD_PRESSURE,
    CONTENT_FARM,
    NO_RISK_SIGNAL,

    /** The stages that ran could not settle the risk, and no later stage runs. */
    NOT_FINAL,
    PAGE_UNSUPPORTED_SCHEME,
    PAGE_SCRIPT_FAILED,

    // The risk-grading model, read by AnswerReader, until the question model replaces it.
    LLM_NOT_LOADED,
    LLM_NO_OBSERVATION,
    LLM_VERDICT,
    LLM_MALFORMED_OUTPUT,
    LLM_UNSUPPORTED_HIGH,
    LLM_UNSUPPORTED_PERSONAL_INFO,
}

data class Verdict(
    val risk: Risk,
    val confidence: Confidence,
    val layer: Layer,
    val reason: ReasonCode,
    val category: RiskCategory = RiskCategory.NONE,
    val host: String? = null,
    /** Variable part of the reason: a brand, a distance, or an error message. */
    val detail: String? = null,
)
