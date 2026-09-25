package com.guardian.ondevice.classifier

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.util.UrlHost
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

class OnDeviceModelJudge(
    private val extractor: UrlFeatureExtractor = UrlFeatureExtractor(),
    private val model: NearestCentroidUrlModel = NearestCentroidUrlModel.default,
) : Judge {
    override suspend fun judge(url: String): Verdict {
        val prediction = model.predict(extractor.extract(url).vector())
        val threshold = model.certainDistanceThresholds.getValue(prediction.label)
        val certain = prediction.label != Risk.UNKNOWN && prediction.distance <= threshold

        return Verdict(
            risk = if (certain) prediction.label else Risk.UNKNOWN,
            confidence = if (certain) Confidence.CERTAIN else Confidence.UNCERTAIN,
            layer = Layer.MODEL,
            reason = if (certain) ReasonCode.MODEL_CONFIDENT else ReasonCode.MODEL_UNCERTAIN,
            host = UrlHost.of(url),
            detail = "${prediction.label} @ ${String.format(Locale.ROOT, "%.4f", prediction.distance)} (model ${model.version})",
        )
    }
}

data class ModelPrediction(
    val label: Risk,
    val distance: Double,
)

class NearestCentroidUrlModel(
    private val featureMin: List<Double>,
    private val featureMax: List<Double>,
    private val centroids: Map<Risk, List<Double>>,
    val certainDistanceThresholds: Map<Risk, Double>,
    val version: String = "unversioned",
) {
    init {
        val size = UrlFeatureExtractor.FEATURE_NAMES.size
        require(featureMin.size == size && featureMax.size == size) { "Feature range size must be $size." }
        require(centroids.isNotEmpty()) { "Model has no centroids." }
        require(centroids.values.all { it.size == size }) { "Every centroid must have $size values." }
        require(certainDistanceThresholds.keys.containsAll(centroids.keys)) {
            "Missing certain-distance thresholds for ${centroids.keys - certainDistanceThresholds.keys}."
        }
    }

    fun predict(vector: List<Double>): ModelPrediction {
        val compressed = compressLengthFeatures(vector)
        val scaled = compressed.mapIndexed { index, value ->
            scale(value, featureMin[index], featureMax[index])
        }

        val nearest = centroids
            .mapValues { (_, centroid) -> euclideanDistance(scaled, centroid) }
            .minWith(compareBy<Map.Entry<Risk, Double>> { it.value }.thenBy { it.key.name })

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
        private val LENGTH_FEATURE_NAMES = setOf("url_length", "host_length", "path_length", "query_length")
        private val LENGTH_FEATURE_INDICES = UrlFeatureExtractor.FEATURE_NAMES
            .mapIndexedNotNull { index, name -> index.takeIf { name in LENGTH_FEATURE_NAMES } }
            .toSet()

        val default: NearestCentroidUrlModel by lazy { fromResource(DEFAULT_MODEL_RESOURCE) }

        fun fromResource(resourceName: String): NearestCentroidUrlModel {
            val stream = NearestCentroidUrlModel::class.java.getResourceAsStream(resourceName)
                ?: error("Model artifact resource not found: $resourceName")
            return fromJson(stream.bufferedReader().use { it.readText() })
        }

        /** Validates the whole artifact up front so a bad label fails at load, not per prediction. */
        fun fromJson(json: String): NearestCentroidUrlModel {
            val artifact = JSONObject(json)
            val type = artifact.getString("type")
            require(type == "nearest-centroid") { "Unsupported model type: $type" }

            val featureNames = artifact.getJSONArray("feature_names").toStringList()
            require(featureNames == UrlFeatureExtractor.FEATURE_NAMES) {
                "Model feature_names do not match UrlFeatureExtractor.FEATURE_NAMES."
            }

            val centroidsJson = artifact.getJSONObject("centroids")
            val centroids = centroidsJson.keys().asSequence().associate { label ->
                riskOf(label) to centroidsJson.getJSONArray(label).toDoubleList()
            }

            val thresholdsJson = artifact.getJSONObject("certain_distance_thresholds")
            val thresholds = thresholdsJson.keys().asSequence().associate { label ->
                riskOf(label) to thresholdsJson.getDouble(label)
            }

            return NearestCentroidUrlModel(
                featureMin = artifact.getJSONArray("feature_min").toDoubleList(),
                featureMax = artifact.getJSONArray("feature_max").toDoubleList(),
                centroids = centroids,
                certainDistanceThresholds = thresholds,
                version = artifact.optString("version", "unversioned"),
            )
        }

        private fun riskOf(label: String): Risk =
            Risk.entries.firstOrNull { it.name == label }
                ?: throw IllegalArgumentException("Unknown model label: $label")
    }
}

private fun JSONArray.toDoubleList(): List<Double> = List(length()) { getDouble(it) }

private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }
