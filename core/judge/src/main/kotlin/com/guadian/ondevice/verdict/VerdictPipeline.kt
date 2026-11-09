package com.guadian.ondevice.verdict

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Judge
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.store.VerdictStore

/** What the stages found so far: facts for the verdict table, and what a reason may show. */
data class Evidence(
    val facts: VerdictTable.Facts,
    val host: String? = null,
    /** The variable part of a reason, such as the listed domain or the official brand, by the reason it goes with. */
    val details: Map<ReasonCode, String> = emptyMap(),
)

/** The URL stage: signals from the URL alone, before the page opens. */
fun interface UrlStage {
    fun inspect(url: String): Evidence
}

/** The page stage: opens the page and adds its signals to what the URL stage found. */
fun interface PageStage {
    suspend fun inspect(url: String, known: Evidence): PageResult
}

sealed interface PageResult {
    data class Observed(val evidence: Evidence) : PageResult

    /** The page could not be inspected at all (an unsupported scheme, a script failure). */
    data class Unreadable(val verdict: Verdict) : PageResult
}

/** The model stage. */
fun interface AnswerStage {
    suspend fun answer(url: String, known: Evidence): AnswerResult
}

sealed interface AnswerResult {
    /** The model's answers added to the facts. */
    data class Answered(val evidence: Evidence) : AnswerResult

    /**
     * The risk-grading model's own verdict, used until the question model replaces it
     * (docs/design.md, section 9, step 5).
     */
    data class Graded(val verdict: Verdict) : AnswerResult
}

/**
 * The judgment pipeline of docs/design.md, section 3: cache and URL, page, on-device
 * LLM, verdict. Only [table] sets a risk. After the URL stage and after the page stage
 * the pipeline asks it whether the result is final and stops there if so (early exit),
 * so the page is opened and the model run only when needed. A pipeline without
 * [page] or [answers] stops before that stage with an uncertain `UNKNOWN`.
 */
class VerdictPipeline(
    private val url: UrlStage,
    private val page: PageStage?,
    private val answers: AnswerStage?,
    private val table: VerdictTable = VerdictTable.default,
    private val store: VerdictStore? = null,
) : Judge {
    override suspend fun judge(url: String): Verdict {
        store?.find(url, table.version)?.let { return it.copy(layer = Layer.STORE) }

        var evidence = this.url.inspect(url)
        decided(evidence, Layer.URL)?.let { return remember(url, it) }

        val page = page ?: return notFinal(evidence, Layer.URL)
        evidence = when (val result = page.inspect(url, evidence)) {
            is PageResult.Unreadable -> return result.verdict
            is PageResult.Observed -> result.evidence
        }
        decided(evidence, Layer.PAGE_CONTENT)?.let { return remember(url, it) }

        val answers = answers ?: return notFinal(evidence, Layer.PAGE_CONTENT)
        return when (val result = answers.answer(url, evidence)) {
            is AnswerResult.Graded -> remember(url, result.verdict)
            is AnswerResult.Answered -> remember(url, verdict(table.decide(result.evidence.facts), result.evidence, Layer.LLM))
        }
    }

    private fun decided(evidence: Evidence, layer: Layer): Verdict? =
        table.decide(evidence.facts).takeIf { it.final }?.let { verdict(it, evidence, layer) }

    private fun remember(url: String, verdict: Verdict): Verdict {
        if (verdict.confidence == Confidence.CERTAIN) store?.save(url, table.version, verdict)
        return verdict
    }

    private fun verdict(decision: VerdictTable.Decision, evidence: Evidence, layer: Layer): Verdict {
        val reason = reasonOf(decision.reason)
        return Verdict(
            risk = Risk.valueOf(decision.risk),
            confidence = Confidence.CERTAIN,
            layer = layer,
            reason = reason,
            category = CATEGORIES[decision.type] ?: RiskCategory.OTHER,
            host = evidence.host,
            detail = evidence.details[reason],
        )
    }

    private fun notFinal(evidence: Evidence, layer: Layer) = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = layer,
        reason = ReasonCode.NOT_FINAL,
        host = evidence.host,
    )

    private companion object {
        /** verdict_table.json types; the app's categories predate them and name two differently. */
        val CATEGORIES = mapOf(
            "none" to RiskCategory.NONE,
            "blocklisted" to RiskCategory.BLOCKLISTED,
            "impersonation" to RiskCategory.BRAND_IMPERSONATION,
            "credentials" to RiskCategory.CREDENTIALS,
            "apk" to RiskCategory.APK,
            "personal_info" to RiskCategory.PERSONAL_INFO,
            "payment" to RiskCategory.PAYMENT,
            "investment" to RiskCategory.INVESTMENT,
            "urgency" to RiskCategory.URGENCY,
            "contentfarm" to RiskCategory.CONTENT_FARM,
            "unverifiable" to RiskCategory.UNVERIFIABLE,
        )

        val ACTIONS = mapOf(
            "browse" to ReasonCode.NO_RISK_SIGNAL,
            "sign_in" to ReasonCode.SIGN_IN_SCREEN,
            "personal_field" to ReasonCode.PERSONAL_FIELD,
            "sensitive_field" to ReasonCode.SENSITIVE_FIELD,
            "apk_link" to ReasonCode.APK_DOWNLOAD,
            "payment" to ReasonCode.PAYMENT_PUSH,
            "investment" to ReasonCode.INVESTMENT_OFFER,
            "reward_pressure" to ReasonCode.REWARD_PRESSURE,
            "content_farm" to ReasonCode.CONTENT_FARM,
        )

        /** The table's reason ("blocklisted", "unverifiable", "suspicious", or "<identity>:<action>") as a code. */
        fun reasonOf(reason: String): ReasonCode = when {
            reason == "blocklisted" -> ReasonCode.BLOCKLISTED
            reason == "unverifiable" -> ReasonCode.PAGE_UNVERIFIABLE
            reason == "suspicious" -> ReasonCode.SUSPICIOUS_HOST
            reason.startsWith("official:") -> ReasonCode.OFFICIAL_SITE
            reason.startsWith("impersonation:") -> ReasonCode.IMPERSONATION
            else -> ACTIONS[reason.substringAfter(':')] ?: error("unknown verdict table reason: $reason")
        }
    }
}
