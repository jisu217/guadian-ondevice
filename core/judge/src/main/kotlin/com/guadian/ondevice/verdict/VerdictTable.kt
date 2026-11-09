package com.guadian.ondevice.verdict

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * The verdict table: the one place that maps signals and the model's answers to a
 * risk (docs/design.md, section 6). It reads `ml/runtime/verdict_table.json`, as
 * ml/guadian_ml/llm/contract/verdict_table.py does; VerdictTableParityTest checks
 * both against the shared fixture.
 *
 * [Facts] fields that are null are not known yet. A [Decision] is final when no
 * value of them could change its risk, which is found by trying every value, so
 * finality holds no criteria of its own.
 *
 * [version] is the SHA-256 of the table file; stored verdicts carry it, so a verdict
 * made under an earlier table is not reused.
 */
class VerdictTable(text: String) {
    val version: String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private val json = JSONObject(text)

    /** What the page claims to be: see [Facts.claim]. */
    enum class Claim(val wireName: String) { NONE("none"), LISTED_OTHER("listed_other"), UNLISTED("unlisted") }

    enum class HostImitation(val wireName: String) { NONE("none"), SUSPICIOUS("suspicious"), CERTAIN("certain") }

    data class Facts(
        // URL signals, known before the page opens (gate A).
        val blocklisted: Boolean = false,
        val hostImitation: HostImitation = HostImitation.NONE,
        // Page signals (gate B).
        val opened: Boolean? = null,
        val official: Boolean? = null,
        val apkLink: Boolean? = null,
        val sensitiveField: Boolean? = null,
        val personalField: Boolean? = null,
        // The model's answers.
        /** Nothing, a brand in the brand table whose domain the landing host is not, or a brand it does not list. */
        val claim: Claim? = null,
        val signIn: Boolean? = null,
        val payment: Boolean? = null,
        val investment: Boolean? = null,
        val rewardPressure: Boolean? = null,
        val contentFarm: Boolean? = null,
    )

    data class Cell(val risk: String, val type: String)

    /** [reason] is "blocklisted", "unverifiable", "suspicious", or "<identity>:<action>". */
    data class Decision(val risk: String, val type: String, val reason: String, val final: Boolean)

    private val blocklisted = json.getJSONArray("blocklisted").cell()
    private val unverifiable = json.getJSONArray("unverifiable").cell()
    private val suspicious = json.getJSONArray("suspicious").cell()
    private val actions: List<Pair<String, Map<String, Cell>>> = json.getJSONArray("actions").let { rows ->
        List(rows.length()) { i ->
            val row = rows.getJSONObject(i)
            row.getString("action") to IDENTITIES.associateWith { row.getJSONArray(it).cell() }
        }
    }

    /** The decision for what is known so far, and whether more facts could change its risk. */
    fun decide(facts: Facts): Decision {
        val (cell, reason) = decideKnown(facts.neutral())
        if (cell.risk == "HIGH") return Decision(cell.risk, cell.type, reason, final = true)  // nothing can raise it
        val final = facts.completions().all { decideKnown(it).first.risk == cell.risk }
        return Decision(cell.risk, cell.type, reason, final)
    }

    /** The table's answer for facts that are all known. */
    private fun decideKnown(f: Facts): Pair<Cell, String> {
        if (f.blocklisted) return blocklisted to "blocklisted"
        val identity = identity(f)
        if (identity == "unverifiable") return unverifiable to "unverifiable"
        var best: Pair<Cell, String>? = null
        for ((action, cells) in actions) {
            if (action == "browse" || f.active(action)) {
                val cell = cells.getValue(identity)
                if (best == null || RANK.getValue(cell.risk) > RANK.getValue(best.first.risk)) {
                    best = cell to "$identity:$action"
                }
            }
        }
        val chosen = checkNotNull(best) { "verdict_table.json has no browse row" }
        if (identity == "unknown" && f.hostImitation == HostImitation.SUSPICIOUS &&
            RANK.getValue(suspicious.risk) > RANK.getValue(chosen.first.risk)
        ) {
            return suspicious to "suspicious"
        }
        return chosen
    }

    private fun identity(f: Facts): String = when {
        f.hostImitation == HostImitation.CERTAIN -> "impersonation"
        f.opened != true -> "unverifiable"
        f.official == true -> "official"
        f.claim == Claim.LISTED_OTHER || (f.claim == Claim.UNLISTED && (f.signIn == true || f.sensitiveField == true)) ->
            "impersonation"
        else -> "unknown"
    }

    private fun Facts.active(action: String): Boolean = when (action) {
        "sign_in" -> signIn
        "personal_field" -> personalField
        "sensitive_field" -> sensitiveField
        "apk_link" -> apkLink
        "payment" -> payment
        "investment" -> investment
        "reward_pressure" -> rewardPressure
        "content_farm" -> contentFarm
        else -> error("unknown action in verdict_table.json: $action")
    } == true

    /** Unknown facts at the values a decision is made with: a page that opened and showed nothing, a model that found nothing. */
    private fun Facts.neutral() = copy(
        opened = opened ?: true, official = official ?: false, apkLink = apkLink ?: false,
        sensitiveField = sensitiveField ?: false, personalField = personalField ?: false,
        claim = claim ?: Claim.NONE, signIn = signIn ?: false, payment = payment ?: false,
        investment = investment ?: false, rewardPressure = rewardPressure ?: false, contentFarm = contentFarm ?: false,
    )

    /** Every way the unknown facts could turn out. */
    private fun Facts.completions(): Sequence<Facts> {
        val bools = listOf(false, true)
        fun <T> options(value: T?, all: List<T>) = if (value == null) all else listOf(value)
        return sequence {
            for (o in options(opened, bools)) for (of in options(official, bools)) for (a in options(apkLink, bools))
            for (s in options(sensitiveField, bools)) for (p in options(personalField, bools))
            for (c in options(claim, Claim.entries)) for (si in options(signIn, bools)) for (pa in options(payment, bools))
            for (i in options(investment, bools)) for (r in options(rewardPressure, bools)) for (cf in options(contentFarm, bools)) {
                yield(copy(opened = o, official = of, apkLink = a, sensitiveField = s, personalField = p, claim = c,
                    signIn = si, payment = pa, investment = i, rewardPressure = r, contentFarm = cf))
            }
        }
    }

    companion object {
        private val IDENTITIES = listOf("official", "impersonation", "unknown")
        private val RANK = mapOf("LOW" to 0, "MEDIUM" to 1, "HIGH" to 2)

        val default: VerdictTable by lazy {
            val stream = VerdictTable::class.java.getResourceAsStream("/verdict_table.json")
                ?: error("verdict_table.json resource is missing")
            VerdictTable(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        private fun JSONArray.cell() = Cell(getString(0), getString(1))
    }
}
