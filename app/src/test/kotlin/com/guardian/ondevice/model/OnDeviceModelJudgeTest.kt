package com.guardian.ondevice.model

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class OnDeviceModelJudgeTest {
    @Test
    fun `predicts low risk for a case under the calibrated distance threshold`() {
        val verdict = judgeNow("https://docs.example.com/support/chat?lang=ko")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("on-device-model", verdict.source)
        assertEquals("nearest_centroid", verdict.type)
    }

    @Test
    fun `predicts high risk for a brand login verification url`() {
        val verdict = judgeNow("https://bank-login-verification.test/account")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
    }

    @Test
    fun `predicts high risk for a suspicious bank lookalike domain`() {
        val verdict = judgeNow("https://kb-star.top/login?verify=account")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
    }

    @Test
    fun `falls back when an apk url is high leaning but above the certain threshold`() {
        val url = "https://download-center.click/install/app.apk?token=12345"
        val verdict = judgeNow(url)

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("HIGH", nearestLabel(url))
    }

    @Test
    fun `falls back for ad click redirect urls instead of making a certain local decision`() {
        val url = "https://ad.planbplus.co.kr/adlanding/?landing_url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSDP%3FpageKey%3D123&adid=4566"
        val verdict = judgeNow(url)

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("MEDIUM", nearestLabel(url))
    }

    // Documents the current URL-only limitation: the nearest centroid leans
    // MEDIUM, but calibrated confidence still sends this case to fallback.
    @Test
    fun `nearest centroid still leans medium for a reward event url, but not confidently`() {
        val verdict = judgeNow("https://promo-reward-event.test/free-gift")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("MEDIUM", nearestLabel("https://promo-reward-event.test/free-gift"))
    }

    @Test
    fun `keeps unknown model label uncertain`() {
        val verdict = judgeNow("https://no-official-match.test/about")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
    }

    private fun judgeNow(url: String): Verdict {
        lateinit var verdict: Verdict
        OnDeviceModelJudge().judge(url) { verdict = it }
        return verdict
    }

    private fun nearestLabel(url: String): String {
        val vector = UrlFeatureExtractor().extract(url).vector()
        return NearestCentroidUrlModel.default().predict(vector).label
    }
}
