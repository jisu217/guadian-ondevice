package com.guadian.ondevice.ui

import android.content.Context
import androidx.annotation.StringRes
import com.guadian.ondevice.R
import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict

/** Turns a Verdict's codes into user-facing text. Exhaustive `when`s keep every code covered. */
class VerdictText(private val context: Context) {

    fun risk(risk: Risk): String = context.getString(
        when (risk) {
            Risk.LOW -> R.string.risk_low
            Risk.MEDIUM -> R.string.risk_medium
            Risk.HIGH -> R.string.risk_high
            Risk.UNKNOWN -> R.string.risk_unknown
        }
    )

    fun confidence(confidence: Confidence): String = context.getString(
        when (confidence) {
            Confidence.CERTAIN -> R.string.confidence_certain
            Confidence.UNCERTAIN -> R.string.confidence_uncertain
        }
    )

    fun layer(layer: Layer): String = context.getString(
        when (layer) {
            Layer.STORE -> R.string.layer_store
            Layer.RULE -> R.string.layer_rule
            Layer.KNOWLEDGE -> R.string.layer_knowledge
            Layer.PAGE_CONTENT -> R.string.layer_page_content
            Layer.LLM -> R.string.layer_llm
        }
    )

    fun reason(verdict: Verdict): String {
        val detail = verdict.detail
        val text = context.getString(reasonRes(verdict.reason), detail.orEmpty())
        val showsDetail = verdict.reason in REASONS_WITH_DETAIL_ARG
        return if (!showsDetail && !detail.isNullOrBlank()) "$text ($detail)" else text
    }

    fun advice(risk: Risk): String = context.getString(
        when (risk) {
            Risk.LOW -> R.string.advice_low
            Risk.MEDIUM -> R.string.advice_medium
            Risk.HIGH -> R.string.advice_high
            Risk.UNKNOWN -> R.string.advice_unknown
        }
    )

    @StringRes
    private fun reasonRes(code: ReasonCode): Int = when (code) {
        ReasonCode.CACHE_MISS -> R.string.reason_cache_miss
        ReasonCode.BLOCKLISTED -> R.string.reason_blocklisted
        ReasonCode.APK_DOWNLOAD -> R.string.reason_apk_download
        ReasonCode.OFFICIAL_DOMAIN -> R.string.reason_official_domain
        ReasonCode.BRAND_TOKEN_IN_HOST -> R.string.reason_brand_token_in_host
        ReasonCode.BRAND_SUBSTRING_IN_HOST -> R.string.reason_brand_substring_in_host
        ReasonCode.SUSPICIOUS_KEYWORD -> R.string.reason_suspicious_keyword
        ReasonCode.NO_RULE_MATCH -> R.string.reason_no_rule_match
        ReasonCode.KNOWLEDGE_MATCH -> R.string.reason_knowledge_match
        ReasonCode.KNOWLEDGE_NO_MATCH -> R.string.reason_knowledge_no_match
        ReasonCode.PAGE_BRAND_LOGIN_FORM -> R.string.reason_page_brand_login_form
        ReasonCode.PAGE_UNVERIFIED_LOGIN_FORM -> R.string.reason_page_unverified_login_form
        ReasonCode.PAGE_BRAND_MENTION_ONLY -> R.string.reason_page_brand_mention_only
        ReasonCode.PAGE_NO_SIGNAL -> R.string.reason_page_no_signal
        ReasonCode.PAGE_UNSUPPORTED_SCHEME -> R.string.reason_page_unsupported_scheme
        ReasonCode.PAGE_LOAD_FAILED -> R.string.reason_page_load_failed
        ReasonCode.PAGE_TIMEOUT -> R.string.reason_page_timeout
        ReasonCode.PAGE_SCRIPT_FAILED -> R.string.reason_page_script_failed
        ReasonCode.LLM_NOT_LOADED -> R.string.reason_llm_not_loaded
        ReasonCode.LLM_NO_OBSERVATION -> R.string.reason_llm_no_observation
        ReasonCode.LLM_VERDICT -> R.string.reason_llm_verdict
        ReasonCode.LLM_MALFORMED_OUTPUT -> R.string.reason_llm_malformed_output
        ReasonCode.LLM_UNSUPPORTED_HIGH -> R.string.reason_llm_unsupported_high
        ReasonCode.LLM_UNSUPPORTED_PERSONAL_INFO -> R.string.reason_llm_unsupported_personal_info
    }

    private companion object {
        // Codes whose string has a %1$s slot for Verdict.detail.
        val REASONS_WITH_DETAIL_ARG = setOf(
            ReasonCode.BLOCKLISTED,
            ReasonCode.OFFICIAL_DOMAIN,
            ReasonCode.BRAND_TOKEN_IN_HOST,
            ReasonCode.BRAND_SUBSTRING_IN_HOST,
            ReasonCode.KNOWLEDGE_MATCH,
            ReasonCode.PAGE_BRAND_LOGIN_FORM,
            ReasonCode.PAGE_BRAND_MENTION_ONLY,
        )
    }
}
