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
        val confidence = if (risk == Risk.UNKNOWN || prediction.distance > model.certainDistanceThreshold) {
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
    val certainDistanceThreshold: Double = DEFAULT_CERTAIN_DISTANCE_THRESHOLD,
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
        private const val DEFAULT_CERTAIN_DISTANCE_THRESHOLD = 1.2

        fun default(): NearestCentroidUrlModel {
            return NearestCentroidUrlModel(
                featureMin = listOf(
                    18.0, 10.0, 0.0, 0.0, 0.0, 0.1590909090909091, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                ),
                featureMax = listOf(
                    50.0, 28.0, 23.0, 17.0, 0.125, 0.2692307692307692, 1.0, 1.0,
                    1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
                ),
                centroids = mapOf(
                    "LOW" to listOf(
                        0.3606770833333333, 0.3148148148148148, 0.2554347826086956, 0.0,
                        0.0, 0.3689227888176369, 0.625, 0.041666666666666664, 0.0,
                        0.0, 0.0, 0.0, 0.125, 1.0, 0.25,
                    ),
                    "MEDIUM" to listOf(
                        0.5221354166666666, 0.42824074074074076, 0.32065217391304346,
                        0.08823529411764706, 0.05949208099745734, 0.3443840413260782,
                        0.041666666666666664, 0.0, 0.041666666666666664, 0.75,
                        0.2916666666666667, 0.16666666666666666, 0.0, 0.0, 1.0,
                    ),
                    "HIGH" to listOf(
                        0.7265625, 0.7245370370370371, 0.38224637681159424,
                        0.0784313725490196, 0.0, 0.20166649706260753, 0.0,
                        0.2916666666666667, 0.5416666666666666, 0.125,
                        0.08333333333333333, 0.0, 0.4166666666666667, 0.0, 1.0,
                    ),
                    "UNKNOWN" to listOf(
                        0.5677083333333334, 0.42824074074074076, 0.36775362318840576,
                        0.11029411764705882, 0.11373873873873874, 0.24316541723947138,
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0,
                    ),
                )
            )
        }
    }
}
