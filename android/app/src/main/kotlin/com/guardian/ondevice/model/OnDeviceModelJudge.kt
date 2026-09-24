package com.guardian.ondevice.model

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import kotlin.math.sqrt

class OnDeviceModelJudge(
    private val extractor: UrlFeatureExtractor = UrlFeatureExtractor(),
    private val model: NearestCentroidUrlModel = NearestCentroidUrlModel.default(),
) : Judge {
    override fun judge(url: String, done: (Verdict) -> Unit) {
        val prediction = model.predict(extractor.extract(url).vector())
        val risk = Risk.valueOf(prediction.label)
        val threshold = model.certainDistanceThresholds.getValue(prediction.label)
        val confidence = if (risk == Risk.UNKNOWN || prediction.distance > threshold) {
            Confidence.UNCERTAIN
        } else {
            Confidence.CERTAIN
        }

        done(
            Verdict(
                risk = if (confidence == Confidence.CERTAIN) risk else Risk.UNKNOWN,
                reason = "Nearest-centroid URL model predicted ${prediction.label} with distance ${prediction.distance.formatDistance()}.",
                advice = adviceFor(risk, confidence),
                source = "on-device-model",
                confidence = confidence,
                type = "nearest_centroid",
            )
        )
    }

    private fun adviceFor(risk: Risk, confidence: Confidence): String {
        if (confidence == Confidence.UNCERTAIN) {
            return "Use knowledge retrieval or remote fallback."
        }

        return when (risk) {
            Risk.LOW -> "Allow the navigation."
            Risk.MEDIUM -> "Ask the user to confirm before opening."
            Risk.HIGH -> "Block or strongly warn before opening."
            Risk.UNKNOWN -> "Use knowledge retrieval or remote fallback."
        }
    }

    private fun Double.formatDistance(): String {
        return "%.4f".format(this)
    }
}

data class ModelPrediction(
    val label: String,
    val distance: Double,
)

class NearestCentroidUrlModel(
    private val featureMin: List<Double>,
    private val featureMax: List<Double>,
    private val centroids: Map<String, List<Double>>,
    val certainDistanceThresholds: Map<String, Double> = DEFAULT_CERTAIN_DISTANCE_THRESHOLDS,
) {
    fun predict(vector: List<Double>): ModelPrediction {
        val scaled = vector.mapIndexed { index, value ->
            scale(value, featureMin[index], featureMax[index])
        }

        val nearest = centroids
            .mapValues { (_, centroid) -> euclideanDistance(scaled, centroid) }
            .minWith(compareBy<Map.Entry<String, Double>> { it.value }.thenBy { it.key })

        return ModelPrediction(label = nearest.key, distance = nearest.value)
    }

    private fun scale(value: Double, minimum: Double, maximum: Double): Double {
        return if (maximum == minimum) 0.0 else (value - minimum) / (maximum - minimum)
    }

    private fun euclideanDistance(left: List<Double>, right: List<Double>): Double {
        return sqrt(left.zip(right).sumOf { (a, b) -> (a - b) * (a - b) })
    }

    companion object {
        // Kept in sync by hand with model/artifacts/url_risk_centroid_model.json.
        // Re-run model/training/train_model.py and copy these values whenever the
        // dataset or feature set changes.
        //
        // One threshold per label, calibrated separately (see
        // calibrate_certain_distance_thresholds in train_model.py) instead of a
        // single shared scalar. A shared scalar meant one close call for any
        // label dragged every other label's threshold down with it — HIGH's
        // threshold once collapsed to 0.35 purely because of an unrelated
        // LOW/MEDIUM boundary case. Per-label thresholds fixed that (HIGH is
        // back to resolving confidently), but MEDIUM still rarely clears its
        // own bar on real traffic — that part is a real, separate limitation
        // of URL-only features, not a threshold artifact. See model/evaluation/
        // trained-model-report.md and docs/interview-brief.md.
        private val DEFAULT_CERTAIN_DISTANCE_THRESHOLDS = mapOf(
            "LOW" to 0.3521711633503199,
            "MEDIUM" to 0.7851810328851682,
            "HIGH" to 1.0400515801994275,
            "UNKNOWN" to 0.3066487667231378,
        )

        fun default(): NearestCentroidUrlModel {
            return NearestCentroidUrlModel(
                featureMin = listOf(
                    9.0, 1.0, 0.0, 0.0, 0.0, 0.03940298507462687, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                ),
                featureMax = listOf(
                    1675.0, 52.0, 1650.0, 729.0, 0.4277227722772277, 0.4444444444444444,
                    3.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
                ),
                centroids = mapOf(
                    "LOW" to listOf(
                        0.060627529700404754, 0.2731168970320369, 0.01570789865871833,
                        0.08339892209554221, 0.1532108139623962, 0.364186453860736,
                        0.3041894353369763, 0.00546448087431694, 0.01092896174863388,
                        0.01092896174863388, 0.0, 0.01639344262295082, 0.15300546448087432,
                        0.7213114754098361, 0.03278688524590164,
                    ),
                    "MEDIUM" to listOf(
                        0.0550583869911601, 0.28843582887700536, 0.00797520661157025,
                        0.0859832896869934, 0.15100843634063002, 0.34538716240734574,
                        0.20643939393939392, 0.0, 0.005681818181818182, 0.4431818181818182,
                        0.03977272727272727, 0.03977272727272727, 0.10227272727272728,
                        0.07386363636363637, 0.45454545454545453,
                    ),
                    "HIGH" to listOf(
                        0.024469046878010464, 0.4531590413943355, 0.008477366255144033,
                        0.0048265000254026314, 0.005627521690492418, 0.34053811046223526,
                        0.004115226337448559, 0.30864197530864196, 0.6296296296296297,
                        0.037037037037037035, 0.024691358024691357, 0.0, 0.37037037037037035,
                        0.0, 0.9876543209876543,
                    ),
                    "UNKNOWN" to listOf(
                        0.018547418967587034, 0.36004901960784313, 0.0072348484848484855,
                        0.0007716049382716049, 0.05520969006058195, 0.376767182884855,
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.3,
                    ),
                )
            )
        }
    }
}
