package com.guardian.ondevice.model

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
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

    // Mirrors _compress_length_features in model/training/train_model.py: a
    // few very long real tracking URLs stretch url_length/host_length/
    // path_length/query_length's min-max range so far that ordinary URLs
    // collapse into a tiny sliver of it. log1p keeps ordering but flattens
    // the tail before scaling, so one extreme URL can't distort distances
    // for everyone else. Must stay in sync with the Python side — a model
    // trained with this compression needs it applied at inference too.
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
            val artifact = JsonReader(json).parseObject()
            val type = artifact.string("type")
            require(type == "nearest-centroid") { "Unsupported model type: $type" }

            val featureNames = artifact.stringList("feature_names")
            require(featureNames == UrlFeatureExtractor.FEATURE_NAMES) {
                "Model feature_names do not match UrlFeatureExtractor.FEATURE_NAMES."
            }

            return NearestCentroidUrlModel(
                featureMin = artifact.doubleList("feature_min"),
                featureMax = artifact.doubleList("feature_max"),
                centroids = artifact.objectValue("centroids").mapValues { (_, value) ->
                    value.asDoubleList()
                },
                certainDistanceThresholds = artifact.objectValue("certain_distance_thresholds")
                    .mapValues { (_, value) -> value.asDouble() },
            )
        }
    }
}

private class JsonReader(private val input: String) {
    private var index = 0

    fun parseObject(): Map<String, JsonValue> {
        val value = parseValue()
        skipWhitespace()
        require(index == input.length) { "Unexpected trailing JSON content at offset $index." }
        return value.asObject()
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        require(index < input.length) { "Unexpected end of JSON input." }
        return when (input[index]) {
            '{' -> JsonValue.ObjectValue(parseObjectValue())
            '[' -> JsonValue.ArrayValue(parseArrayValue())
            '"' -> JsonValue.StringValue(parseStringValue())
            '-', in '0'..'9' -> JsonValue.NumberValue(parseNumberValue())
            't' -> parseLiteral("true", JsonValue.BooleanValue(true))
            'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
            'n' -> parseLiteral("null", JsonValue.NullValue)
            else -> error("Unexpected JSON token '${input[index]}' at offset $index.")
        }
    }

    private fun parseObjectValue(): Map<String, JsonValue> {
        expect('{')
        skipWhitespace()
        if (peek('}')) {
            index++
            return emptyMap()
        }

        val values = linkedMapOf<String, JsonValue>()
        while (true) {
            skipWhitespace()
            val key = parseStringValue()
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    return values
                }
                else -> error("Expected ',' or '}' at offset $index.")
            }
        }
    }

    private fun parseArrayValue(): List<JsonValue> {
        expect('[')
        skipWhitespace()
        if (peek(']')) {
            index++
            return emptyList()
        }

        val values = mutableListOf<JsonValue>()
        while (true) {
            values += parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    return values
                }
                else -> error("Expected ',' or ']' at offset $index.")
            }
        }
    }

    private fun parseStringValue(): String {
        expect('"')
        val out = StringBuilder()
        while (index < input.length) {
            val char = input[index++]
            when (char) {
                '"' -> return out.toString()
                '\\' -> out.append(parseEscapedChar())
                else -> out.append(char)
            }
        }
        error("Unterminated JSON string.")
    }

    private fun parseEscapedChar(): Char {
        require(index < input.length) { "Unterminated JSON escape." }
        return when (val escaped = input[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                val hex = input.substring(index, index + 4)
                index += 4
                hex.toInt(16).toChar()
            }
            else -> error("Unsupported JSON escape '\\$escaped' at offset ${index - 1}.")
        }
    }

    private fun parseNumberValue(): Double {
        val start = index
        if (peek('-')) index++
        consumeDigits()
        if (peek('.')) {
            index++
            consumeDigits()
        }
        if (peek('e') || peek('E')) {
            index++
            if (peek('+') || peek('-')) index++
            consumeDigits()
        }
        return input.substring(start, index).toDouble()
    }

    private fun consumeDigits() {
        val start = index
        while (index < input.length && input[index].isDigit()) {
            index++
        }
        require(index > start) { "Expected digit at offset $index." }
    }

    private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
        require(input.startsWith(literal, index)) { "Expected '$literal' at offset $index." }
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (index < input.length && input[index].isWhitespace()) {
            index++
        }
    }

    private fun expect(char: Char) {
        skipWhitespace()
        require(peek(char)) { "Expected '$char' at offset $index." }
        index++
    }

    private fun peek(char: Char): Boolean {
        return index < input.length && input[index] == char
    }
}

private sealed class JsonValue {
    data class ObjectValue(val value: Map<String, JsonValue>) : JsonValue()
    data class ArrayValue(val value: List<JsonValue>) : JsonValue()
    data class StringValue(val value: String) : JsonValue()
    data class NumberValue(val value: Double) : JsonValue()
    data class BooleanValue(val value: Boolean) : JsonValue()
    data object NullValue : JsonValue()

    fun asObject(): Map<String, JsonValue> = (this as ObjectValue).value
    fun asArray(): List<JsonValue> = (this as ArrayValue).value
    fun asString(): String = (this as StringValue).value
    fun asDouble(): Double = (this as NumberValue).value
    fun asDoubleList(): List<Double> = asArray().map { it.asDouble() }
}

private fun Map<String, JsonValue>.string(key: String): String {
    return requireNotNull(this[key]) { "Missing JSON string field: $key" }.asString()
}

private fun Map<String, JsonValue>.stringList(key: String): List<String> {
    return requireNotNull(this[key]) { "Missing JSON string array field: $key" }
        .asArray()
        .map { it.asString() }
}

private fun Map<String, JsonValue>.doubleList(key: String): List<Double> {
    return requireNotNull(this[key]) { "Missing JSON number array field: $key" }.asDoubleList()
}

private fun Map<String, JsonValue>.objectValue(key: String): Map<String, JsonValue> {
    return requireNotNull(this[key]) { "Missing JSON object field: $key" }.asObject()
}
