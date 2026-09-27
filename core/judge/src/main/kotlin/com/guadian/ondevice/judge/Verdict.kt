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
    REMOTE("remote"),
}

/**
 * What kind of risk a verdict describes. [wireName] is the snake_case value
 * shared with server/judge_service.py's `category` field.
 */
enum class RiskCategory(val wireName: String) {
    NONE("none"),
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

    /** A server category this app version does not know yet. */
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
    REMOTE_VERDICT,
    REMOTE_NOT_CONFIGURED,
    REMOTE_FAILED,
}

data class Verdict(
    val risk: Risk,
    val confidence: Confidence,
    val layer: Layer,
    val reason: ReasonCode,
    val category: RiskCategory = RiskCategory.NONE,
    val host: String? = null,
    /** Variable part of the reason: a brand, a distance, or remote free text. */
    val detail: String? = null,
)
