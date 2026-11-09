package com.guadian.ondevice.llm

import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.verdict.Evidence

/**
 * The page stage's signals (docs/design.md, section 4), computed from what page
 * inspection saw with the same rules the training labels follow: the brand's own
 * site after redirects, whether the page opened, `.apk` links, resident registration
 * or card number fields, and personal fields (labeling guide, rule 5).
 */
class PageFacts(
    private val brands: LlmBrands = LlmBrands.default,
    private val hostRules: HostRules = HostRules(brands),
    private val pageChars: Int = PromptBuilder.DEFAULT_PAGE_CHARS,
) {
    /** [known] from the URL stage, with the page's signals added. */
    fun evidence(obs: PageObservation, known: Evidence): Evidence {
        val landingUrl = if (obs.loaded) obs.finalUrl ?: obs.url else obs.url
        val landing = PyText.asciiHost(landingUrl)
        val details = known.details.toMutableMap()
        val brand = if (obs.loaded) brands.officialSiteOf(landing)?.also { details[ReasonCode.OFFICIAL_SITE] = it } else null
        val imitation = hostRules.gradeCase(obs.url, if (obs.loaded) landingUrl else null)
            ?.also { details += UrlSignals.imitationDetails(it) }
        val asks = obs.forms.flatMap { it.asks }.toSet()
        // The form kinds as the prompt's 입력 칸 line lists them: unnamed fields only are "일반".
        val kinds = if (obs.forms.isEmpty()) emptySet() else asks.ifEmpty { setOf("일반") }
        val body = if (obs.loaded) PyText.take(obs.text, pageChars) else ""

        val facts = known.facts.copy(
            hostImitation = UrlSignals.hostImitation(imitation),
            opened = obs.loaded && (obs.status ?: 0) < 400,
            official = brand != null,
            apkLink = obs.apkLinks.isNotEmpty(),
            sensitiveField = asks.any { it in SENSITIVE_ASKS },
            personalField = asks.any { it in PERSONAL_ASKS } || AnswerReader.formWordedForContact(kinds, body),
        )
        return Evidence(facts, host = landing.ifEmpty { known.host }, details = details)
    }

    private companion object {
        val SENSITIVE_ASKS = setOf("rrn", "card")
        val PERSONAL_ASKS = setOf("name", "phone")
    }
}
