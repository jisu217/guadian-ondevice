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

    @Test
    fun `predicts high risk for a brand login verification url`() {
        val verdict = judgeNow("https://bank-login-verification.test/account")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
    }

    // Distance thresholds are calibrated per label against real ad-landing
    // traffic (model/data/sample_urls.csv's reference-derived rows) instead of
    // one scalar shared by all 4 labels — see calibrate_certain_distance_
    // thresholds in train_model.py. That fixed HIGH (above), which had been
    // collapsed by an unrelated LOW/MEDIUM boundary case. MEDIUM still rarely
    // clears its own bar on real traffic, and that part is a real, separate
    // limitation: URL text alone doesn't carry the page-content signal (forms,
    // JS-only rendering) that most real MEDIUM cases need. This test documents
    // that limitation instead of hiding it — the nearest-centroid direction is
    // still right, it's just not confident enough to short-circuit the hybrid
    // pipeline on its own.
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
