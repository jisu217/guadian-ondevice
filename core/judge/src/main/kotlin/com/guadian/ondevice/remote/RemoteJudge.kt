package com.guadian.ondevice.remote

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Judge
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.RiskCategory
import com.guadian.ondevice.judge.Verdict
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Last-resort server judgment. [baseUrl] comes from BuildConfig.REMOTE_JUDGE_URL;
 * when it is blank this layer answers UNKNOWN without any network access.
 */
class RemoteJudge(
    baseUrl: String?,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Judge {
    private val judgeUrl: String? = baseUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { "${it.trimEnd('/')}/judge" }

    override suspend fun judge(url: String): Verdict {
        val endpoint = judgeUrl ?: return unknown(ReasonCode.REMOTE_NOT_CONFIGURED)
        return runInterruptible(dispatcher) { call(endpoint, RemoteUrlRedactor.redact(url)) }
    }

    private fun call(endpoint: String, url: String): Verdict {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            conn.outputStream.use { output ->
                output.write(JSONObject().put("url", url).toString().toByteArray(Charsets.UTF_8))
            }

            val status = conn.responseCode
            if (status !in 200..299) {
                conn.errorStream?.close()
                return unknown(ReasonCode.REMOTE_FAILED, "HTTP $status")
            }

            parseVerdict(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            unknown(ReasonCode.REMOTE_FAILED, e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseVerdict(body: String): Verdict {
        val json = runCatching { JSONObject(body.trim()) }.getOrNull()
            ?: return unknown(ReasonCode.REMOTE_FAILED, "unparseable response")

        val risk = Risk.entries.firstOrNull { it.name == json.optString("risk") } ?: Risk.UNKNOWN
        val reason = when (json.optString("status", "ok")) {
            "not_configured" -> ReasonCode.REMOTE_NOT_CONFIGURED
            "error" -> ReasonCode.REMOTE_FAILED
            else -> ReasonCode.REMOTE_VERDICT
        }

        return Verdict(
            risk = risk,
            confidence = if (risk == Risk.UNKNOWN) Confidence.UNCERTAIN else Confidence.CERTAIN,
            layer = Layer.REMOTE,
            reason = reason,
            category = RiskCategory.fromWire(json.optStringOrNull("category")),
            host = json.optStringOrNull("host"),
            detail = json.optStringOrNull("reason"),
        )
    }

    private fun unknown(reason: ReasonCode, detail: String? = null) = Verdict(
        risk = Risk.UNKNOWN,
        confidence = Confidence.UNCERTAIN,
        layer = Layer.REMOTE,
        reason = reason,
        detail = detail,
    )

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000
        const val DEFAULT_READ_TIMEOUT_MS = 10_000
    }
}
