package com.guadian.ondevice.classifier

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnDeviceModelJudgeTest {
    @Test
    fun `predicts low risk for a case under the calibrated distance threshold`() {
        val verdict = judgeNow("https://docs.example.com/support/chat?lang=ko")

        assertEquals(Risk.LOW, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(Layer.MODEL, verdict.layer)
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
        assertEquals(Risk.HIGH, nearestLabel(url))
    }

    @Test
    fun `falls back for ad click redirect urls instead of making a certain local decision`() {
        val url = "https://ad.planbplus.co.kr/adlanding/?landing_url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSDP%3FpageKey%3D123&adid=4566"
        val verdict = judgeNow(url)

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(Risk.MEDIUM, nearestLabel(url))
    }

    // Documents the current URL-only limitation: the nearest centroid leans
    // MEDIUM, but calibrated confidence still sends this case to fallback.
    @Test
    fun `nearest centroid still leans medium for a reward event url, but not confidently`() {
        val verdict = judgeNow("https://promo-reward-event.test/free-gift")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(Risk.MEDIUM, nearestLabel("https://promo-reward-event.test/free-gift"))
    }

    @Test
    fun `keeps unknown model label uncertain`() {
        val verdict = judgeNow("https://no-official-match.test/about")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
    }

    @Test
    fun `bundled artifact carries a model version`() {
        assertTrue(NearestCentroidUrlModel.default.version.startsWith("v"))
    }

    @Test
    fun `rejects an artifact with an unknown label at load time`() {
        val json = artifactJson(labels = listOf("LOW", "SEVERE"))

        assertFailsWith<IllegalArgumentException> { NearestCentroidUrlModel.fromJson(json) }
    }

    @Test
    fun `rejects an artifact missing a threshold for a centroid label`() {
        val json = artifactJson(labels = listOf("LOW", "HIGH"), thresholdLabels = listOf("LOW"))

        assertFailsWith<IllegalArgumentException> { NearestCentroidUrlModel.fromJson(json) }
    }

    private fun artifactJson(labels: List<String>, thresholdLabels: List<String> = labels): String {
        val size = UrlFeatureExtractor.FEATURE_NAMES.size
        val zeros = List(size) { 0.0 }.joinToString(",", "[", "]")
        val names = UrlFeatureExtractor.FEATURE_NAMES.joinToString(",", "[", "]") { "\"$it\"" }
        val centroids = labels.joinToString(",", "{", "}") { "\"$it\":$zeros" }
        val thresholds = thresholdLabels.joinToString(",", "{", "}") { "\"$it\":1.0" }
        return """{"type":"nearest-centroid","feature_names":$names,"feature_min":$zeros,"feature_max":$zeros,""" +
            """"centroids":$centroids,"certain_distance_thresholds":$thresholds}"""
    }

    private fun judgeNow(url: String): Verdict = runBlocking { OnDeviceModelJudge().judge(url) }

    private fun nearestLabel(url: String): Risk {
        val vector = UrlFeatureExtractor().extract(url).vector()
        return NearestCentroidUrlModel.default.predict(vector).label
    }
}
