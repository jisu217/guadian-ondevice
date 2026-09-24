package com.guardian.ondevice.judge

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
                output.write("""{"url":"${url.escapeJson()}","stream":true}""".toByteArray())
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

    private fun parseVerdict(body: String): Verdict {
        val json = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.booleanField("early") == true }
            .lastOrNull()
            ?: body.trim()

        val risk = when (json.stringField("risk")) {
            "LOW" -> Risk.LOW
            "MEDIUM" -> Risk.MEDIUM
            "HIGH" -> Risk.HIGH
            else -> Risk.UNKNOWN
        }

        return Verdict(
            risk = risk,
            reason = json.stringField("reason") ?: "Remote judge did not provide a reason.",
            advice = json.stringField("advice") ?: "Keep the user in the safe confirmation flow.",
            source = "remote",
            confidence = if (risk == Risk.UNKNOWN) Confidence.UNCERTAIN else Confidence.CERTAIN,
            site = json.stringField("site"),
            type = json.stringField("type") ?: "none",
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

    private fun String.stringField(name: String): String? {
        val pattern = Regex(""""$name"\s*:\s*"((?:\\.|[^"])*)"""")
        return pattern.find(this)?.groupValues?.get(1)?.unescapeJson()
    }

    private fun String.booleanField(name: String): Boolean? {
        val pattern = Regex(""""$name"\s*:\s*(true|false)""")
        return pattern.find(this)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    private fun String.escapeJson(): String {
        return buildString {
            for (char in this@escapeJson) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun String.unescapeJson(): String {
        return replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://adalert-judge.azurewebsites.net"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000
        const val DEFAULT_READ_TIMEOUT_MS = 10_000
    }
}
