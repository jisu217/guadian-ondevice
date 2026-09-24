package com.guardian.ondevice.judge

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class RemoteJudge(
    baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val executor: Executor = Executors.newCachedThreadPool(),
) : Judge {
    private val judgeUrl = "${baseUrl.trimEnd('/')}/judge"

    override fun judge(url: String, done: (Verdict) -> Unit) {
        executor.execute {
            done(call(url))
        }
    }

    private fun call(url: String): Verdict {
        return try {
            val conn = (URL(judgeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/x-ndjson, application/json")
            }

            conn.outputStream.use { output ->
                val payload = JSONObject().put("url", url).put("stream", true).toString()
                output.write(payload.toByteArray())
            }

            if (conn.responseCode !in 200..299) {
                return unknown("Remote judge returned HTTP ${conn.responseCode}.")
            }

            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            parseVerdict(body)
        } catch (e: Exception) {
            unknown("Remote judge failed: ${e.javaClass.simpleName}.")
        }
    }

    // The server can stream several NDJSON lines (early guesses marked
    // "early": true, then a final line); only the final, non-early line
    // matters here. Falls back to parsing the whole body as one JSON object
    // for a non-streamed response.
    private fun parseVerdict(body: String): Verdict {
        val json = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
            .filterNot { it.optBoolean("early", false) }
            .lastOrNull()
            ?: runCatching { JSONObject(body.trim()) }.getOrNull()
            ?: return unknown("Remote judge returned an unparseable response.")

        val risk = when (json.optString("risk")) {
            "LOW" -> Risk.LOW
            "MEDIUM" -> Risk.MEDIUM
            "HIGH" -> Risk.HIGH
            else -> Risk.UNKNOWN
        }

        return Verdict(
            risk = risk,
            reason = json.optStringOrNull("reason") ?: "Remote judge did not provide a reason.",
            advice = json.optStringOrNull("advice") ?: "Keep the user in the safe confirmation flow.",
            source = "remote",
            confidence = if (risk == Risk.UNKNOWN) Confidence.UNCERTAIN else Confidence.CERTAIN,
            site = json.optStringOrNull("site"),
            type = json.optStringOrNull("type") ?: "none",
        )
    }

    private fun unknown(reason: String): Verdict {
        return Verdict(
            risk = Risk.UNKNOWN,
            reason = reason,
            advice = "Keep the user in the safe confirmation flow.",
            source = "remote",
            confidence = Confidence.UNCERTAIN,
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://adalert-judge.azurewebsites.net"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000
        const val DEFAULT_READ_TIMEOUT_MS = 10_000
    }
}
