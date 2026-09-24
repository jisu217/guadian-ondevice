package com.guardian.ondevice.model

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
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
        val compressed = compressLengthFeatures(vector)
        val scaled = compressed.mapIndexed { index, value ->
            scale(value, featureMin[index], featureMax[index])
        }

        val nearest = centroids
            .mapValues { (_, centroid) -> euclideanDistance(scaled, centroid) }
            .minWith(compareBy<Map.Entry<String, Double>> { it.value }.thenBy { it.key })

        return ModelPrediction(label = nearest.key, distance = nearest.value)
    }

    // Must mirror train_model.py: compress long-tail URL length features before scaling.
    private fun compressLengthFeatures(vector: List<Double>): List<Double> {
        return vector.mapIndexed { index, value ->
            if (index in LENGTH_FEATURE_INDICES) ln(1.0 + value) else value
        }
    }

    private fun scale(value: Double, minimum: Double, maximum: Double): Double {
        return if (maximum == minimum) 0.0 else (value - minimum) / (maximum - minimum)
    }

    private fun euclideanDistance(left: List<Double>, right: List<Double>): Double {
        return sqrt(left.zip(right).sumOf { (a, b) -> (a - b) * (a - b) })
    }

    companion object {
        private const val DEFAULT_MODEL_RESOURCE = "/url_risk_centroid_model.json"
        private val DEFAULT_CERTAIN_DISTANCE_THRESHOLDS = Risk.entries.associate { it.name to 1.2 }
        private val LENGTH_FEATURE_NAMES = setOf("url_length", "host_length", "path_length", "query_length")
        private val LENGTH_FEATURE_INDICES = UrlFeatureExtractor.FEATURE_NAMES
            .mapIndexedNotNull { index, name -> index.takeIf { name in LENGTH_FEATURE_NAMES } }
            .toSet()

        fun default(): NearestCentroidUrlModel {
            return fromResource(DEFAULT_MODEL_RESOURCE)
        }

        fun fromResource(resourceName: String): NearestCentroidUrlModel {
            val stream = NearestCentroidUrlModel::class.java.getResourceAsStream(resourceName)
                ?: error("Model artifact resource not found: $resourceName")
            return fromJson(stream.bufferedReader().readText())
        }

        fun fromJson(json: String): NearestCentroidUrlModel {
            val artifact = JSONObject(json)
            val type = artifact.getString("type")
            require(type == "nearest-centroid") { "Unsupported model type: $type" }

            val featureNames = artifact.getJSONArray("feature_names").toStringList()
            require(featureNames == UrlFeatureExtractor.FEATURE_NAMES) {
                "Model feature_names do not match UrlFeatureExtractor.FEATURE_NAMES."
            }

            val centroidsJson = artifact.getJSONObject("centroids")
            val centroids = centroidsJson.keys().asSequence().associateWith { label ->
                centroidsJson.getJSONArray(label).toDoubleList()
            }

            val thresholdsJson = artifact.getJSONObject("certain_distance_thresholds")
            val thresholds = thresholdsJson.keys().asSequence().associateWith { label ->
                thresholdsJson.getDouble(label)
            }

            return NearestCentroidUrlModel(
                featureMin = artifact.getJSONArray("feature_min").toDoubleList(),
                featureMax = artifact.getJSONArray("feature_max").toDoubleList(),
                centroids = centroids,
                certainDistanceThresholds = thresholds,
            )
        }
    }
}

private fun JSONArray.toDoubleList(): List<Double> = List(length()) { getDouble(it) }

private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }
