package com.guardian.ondevice.model

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class OnDeviceModelJudgeTest {
    @Test
    fun `predicts low risk for a case under the calibrated distance threshold`() {
        val verdict = judgeNow("https://docs.example.com/status/incidents/2026")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("on-device-model", verdict.source)
        assertEquals("nearest_centroid", verdict.type)
    }

    // The distance threshold is now calibrated against real ad-landing traffic
    // (model/data/sample_urls.csv's reference-derived rows), not just synthetic
    // fixtures. Under that calibration, no MEDIUM or HIGH case in the training
    // set clears the "certain" bar on URL text alone — a handful of very long
    // real tracking URLs stretch the min-max feature scale enough to compress
    // everything else together. See model/evaluation/trained-model-report.md.
    // These two tests document that real limitation instead of hiding it:
    // the *direction* of the nearest-centroid guess is still right, it's just
    // never confident enough on its own to short-circuit the hybrid pipeline.
    @Test
    fun `nearest centroid still leans medium for a reward event url, but not confidently`() {
        val verdict = judgeNow("https://promo-reward-event.test/free-gift")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("MEDIUM", nearestLabel("https://promo-reward-event.test/free-gift"))
    }

    @Test
    fun `nearest centroid still leans high for a brand login verification url, but not confidently`() {
        val verdict = judgeNow("https://bank-login-verification.test/account")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("HIGH", nearestLabel("https://bank-login-verification.test/account"))
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
