package com.guardian.ondevice.remote

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.RiskCategory
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteJudgeTest {
    private var server: HttpServer? = null
    private var lastRequestBody: String? = null

    @AfterTest
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `parses a verdict`() = runBlocking {
        startServer(
            status = 200,
            body = """{"risk":"HIGH","reason":"Brand impersonation detected.","advice":"Block.","host":"fake.example","category":"brand_impersonation","status":"ok"}"""
        )

        val verdict = RemoteJudge(baseUrl = serverUrl()).judge("https://fake.example")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals(Layer.REMOTE, verdict.layer)
        assertEquals(ReasonCode.REMOTE_VERDICT, verdict.reason)
        assertEquals("Brand impersonation detected.", verdict.detail)
        assertEquals("fake.example", verdict.host)
        assertEquals(RiskCategory.BRAND_IMPERSONATION, verdict.category)
    }

    @Test
    fun `maps an unknown server category to OTHER instead of failing`() = runBlocking {
        startServer(status = 200, body = """{"risk":"MEDIUM","category":"brand_new_category"}""")

        val verdict = RemoteJudge(baseUrl = serverUrl()).judge("https://example.com")

        assertEquals(RiskCategory.OTHER, verdict.category)
    }

    @Test
    fun `server status not_configured becomes REMOTE_NOT_CONFIGURED`() = runBlocking {
        startServer(status = 200, body = """{"risk":"UNKNOWN","reason":"Remote judge is not configured.","status":"not_configured"}""")

        val verdict = RemoteJudge(baseUrl = serverUrl()).judge("https://example.com")

        assertEquals(ReasonCode.REMOTE_NOT_CONFIGURED, verdict.reason)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
    }

    @Test
    fun `returns unknown when remote call fails`() = runBlocking {
        startServer(status = 500, body = "server error")

        val verdict = RemoteJudge(baseUrl = serverUrl()).judge("https://example.com")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals(ReasonCode.REMOTE_FAILED, verdict.reason)
    }

    @Test
    fun `blank base url answers not configured without any network call`() = runBlocking {
        for (baseUrl in listOf(null, "", "  ")) {
            val verdict = RemoteJudge(baseUrl = baseUrl).judge("https://example.com")

            assertEquals(ReasonCode.REMOTE_NOT_CONFIGURED, verdict.reason)
            assertEquals(Risk.UNKNOWN, verdict.risk)
        }
    }

    @Test
    fun `sends the redacted url`() = runBlocking {
        startServer(status = 200, body = """{"risk":"LOW"}""")

        RemoteJudge(baseUrl = serverUrl()).judge("https://user:pw@example.com/reset?token=secret#frag")

        assertEquals("https://example.com/reset?token=_", JSONObject(lastRequestBody!!).getString("url"))
    }

    private fun startServer(status: Int, body: String) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/judge") { exchange ->
                lastRequestBody = exchange.requestBody.bufferedReader().use { it.readText() }
                exchange.respond(status, body)
            }
            executor = Executors.newSingleThreadExecutor()
            start()
        }
    }

    private fun serverUrl(): String {
        val address = server?.address ?: error("Server is not started.")
        return "http://127.0.0.1:${address.port}"
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
