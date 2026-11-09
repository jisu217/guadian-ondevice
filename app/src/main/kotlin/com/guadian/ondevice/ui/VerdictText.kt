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
            Layer.URL -> R.string.layer_url
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
        ReasonCode.BLOCKLISTED -> R.string.reason_blocklisted
        ReasonCode.OFFICIAL_SITE -> R.string.reason_official_site
        ReasonCode.IMPERSONATION -> R.string.reason_impersonation
        ReasonCode.SUSPICIOUS_HOST -> R.string.reason_suspicious_host
        ReasonCode.PAGE_UNVERIFIABLE -> R.string.reason_page_unverifiable
        ReasonCode.SIGN_IN_SCREEN -> R.string.reason_sign_in_screen
        ReasonCode.PERSONAL_FIELD -> R.string.reason_personal_field
        ReasonCode.SENSITIVE_FIELD -> R.string.reason_sensitive_field
        ReasonCode.APK_DOWNLOAD -> R.string.reason_apk_download
        ReasonCode.PAYMENT_PUSH -> R.string.reason_payment_push
        ReasonCode.INVESTMENT_OFFER -> R.string.reason_investment_offer
        ReasonCode.REWARD_PRESSURE -> R.string.reason_reward_pressure
        ReasonCode.CONTENT_FARM -> R.string.reason_content_farm
        ReasonCode.NO_RISK_SIGNAL -> R.string.reason_no_risk_signal
        ReasonCode.NOT_FINAL -> R.string.reason_not_final
        ReasonCode.PAGE_UNSUPPORTED_SCHEME -> R.string.reason_page_unsupported_scheme
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
            ReasonCode.OFFICIAL_SITE,
        )
    }
}
