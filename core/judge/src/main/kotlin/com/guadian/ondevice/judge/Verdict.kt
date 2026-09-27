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

/** Pipeline stage that produced a verdict. */
enum class Layer(val wireName: String) {
    STORE("local-store"),
    RULE("rule"),
    MODEL("on-device-model"),
    KNOWLEDGE("local-knowledge"),
    PAGE_CONTENT("page-content"),
    LLM("on-device-llm"),
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
    SUSPICIOUS_URL("suspicious_url"),
    PERSONAL_INFO("personal_info"),
    URGENCY("urgency"),
    PAGE_BRAND_IMPERSONATION("page_brand_impersonation"),
    UNVERIFIED_LOGIN_FORM("unverified_login_form"),
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
    CACHE_MISS,
    BLOCKLISTED,
    APK_DOWNLOAD,
    OFFICIAL_DOMAIN,
    BRAND_TOKEN_IN_HOST,
    BRAND_SUBSTRING_IN_HOST,
    SUSPICIOUS_KEYWORD,
    NO_RULE_MATCH,
    MODEL_CONFIDENT,
    MODEL_UNCERTAIN,
    KNOWLEDGE_MATCH,
    KNOWLEDGE_NO_MATCH,
    PAGE_BRAND_LOGIN_FORM,
    PAGE_UNVERIFIED_LOGIN_FORM,
    PAGE_BRAND_MENTION_ONLY,
    PAGE_NO_SIGNAL,
    PAGE_UNSUPPORTED_SCHEME,
    PAGE_LOAD_FAILED,
    PAGE_TIMEOUT,
    PAGE_SCRIPT_FAILED,
    LLM_NOT_LOADED,
    LLM_NO_OBSERVATION,
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
