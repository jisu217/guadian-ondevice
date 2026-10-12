package com.guadian.ondevice.verdict

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.store.InMemoryVerdictStore
import com.guadian.ondevice.verdict.VerdictTable.Claim
import com.guadian.ondevice.verdict.VerdictTable.Facts
import com.guadian.ondevice.verdict.VerdictTable.HostImitation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VerdictPipelineTest {
    private val url = "https://shop.example.com/"
    private val quietPage = Facts(opened = true, official = false, apkLink = false, sensitiveField = false, personalField = false)
    private val quietAnswers = Facts(
        claim = Claim.NONE, signIn = false, payment = false, investment = false, rewardPressure = false, contentFarm = false,
    )

    private var pageCalls = 0
    private var answerCalls = 0

    private fun pipeline(
        urlFacts: Facts = Facts(),
        pageFacts: Facts = quietPage,
        answerFacts: Facts = quietAnswers,
        details: Map<ReasonCode, String> = emptyMap(),
        withPage: Boolean = true,
        withAnswers: Boolean = true,
        store: InMemoryVerdictStore? = null,
    ) = VerdictPipeline(
        url = { Evidence(urlFacts, host = "shop.example.com", details = details) },
        page = if (!withPage) null else PageStage { _, known ->
            pageCalls++
            PageResult.Observed(known.copy(facts = known.facts.merge(pageFacts)))
        },
        answers = if (!withAnswers) null else AnswerStage { _, known ->
            answerCalls++
            AnswerResult.Answered(known.copy(facts = known.facts.merge(answerFacts)))
        },
        store = store,
    )

    @Test
    fun `a blocklisted url ends before the page opens`() = runTest {
        val verdict = pipeline(Facts(blocklisted = true), details = mapOf(ReasonCode.BLOCKLISTED to "evil.example")).judge(url)

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Layer.URL, verdict.layer)
        assertEquals(ReasonCode.BLOCKLISTED, verdict.reason)
        assertEquals("evil.example", verdict.detail)
        assertEquals(0, pageCalls)
    }

    @Test
    fun `an official landing ends before the model`() = runTest {
        val verdict = pipeline(pageFacts = quietPage.copy(official = true, apkLink = true)).judge(url)

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Layer.PAGE_CONTENT, verdict.layer)
        assertEquals(ReasonCode.OFFICIAL_SITE, verdict.reason)
        assertEquals(0, answerCalls)
    }

    @Test
    fun `a personal field waits for the model, which can raise it`() = runTest {
        val verdict = pipeline(
            Facts(hostImitation = HostImitation.NONE),
            pageFacts = quietPage.copy(personalField = true),
            answerFacts = quietAnswers.copy(claim = Claim.LISTED_OTHER),
        ).judge(url)

        assertEquals(1, answerCalls)
        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Layer.LLM, verdict.layer)
        assertEquals(RiskCategory.BRAND_IMPERSONATION, verdict.category)
    }

    @Test
    fun `without a model the result is uncertain unknown`() = runTest {
        val verdict = pipeline(pageFacts = quietPage.copy(personalField = true), withAnswers = false).judge(url)

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(ReasonCode.NOT_FINAL, verdict.reason)
    }

    @Test
    fun `a graded model verdict is used as it is`() = runTest {
        val graded = Verdict(Risk.MEDIUM, Confidence.CERTAIN, Layer.LLM, ReasonCode.LLM_VERDICT, RiskCategory.PAYMENT)
        val verdict = VerdictPipeline(
            url = { Evidence(Facts()) },
            page = { _, known -> PageResult.Observed(known.copy(facts = known.facts.merge(quietPage))) },
            answers = { _, _ -> AnswerResult.Graded(graded) },
        ).judge(url)

        assertEquals(graded, verdict)
    }

    @Test
    fun `a stored verdict is reused only under the same table`() = runTest {
        val store = InMemoryVerdictStore()
        pipeline(Facts(blocklisted = true), store = store).judge(url)

        val again = pipeline(store = store).judge(url)

        assertEquals(Layer.STORE, again.layer)
        assertEquals(Risk.HIGH, again.risk)
        store.save(url, "an older table", again.copy(risk = Risk.LOW))
        assertEquals(Risk.LOW, pipeline(store = store).judge(url).risk)
    }

    /** [other]'s known fields over this one's. */
    private fun Facts.merge(other: Facts) = copy(
        opened = other.opened ?: opened, official = other.official ?: official, apkLink = other.apkLink ?: apkLink,
        sensitiveField = other.sensitiveField ?: sensitiveField, personalField = other.personalField ?: personalField,
        claim = other.claim ?: claim, signIn = other.signIn ?: signIn, payment = other.payment ?: payment,
        investment = other.investment ?: investment, rewardPressure = other.rewardPressure ?: rewardPressure,
        contentFarm = other.contentFarm ?: contentFarm,
    )
}
