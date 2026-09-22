package com.guardian.ondevice.judge

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteJudgeTest {
    private var server: HttpServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `parses final ndjson verdict`() {
        startServer(
            status = 200,
            body = """
                {"early":true,"risk":"LOW","site":"example.com"}
                {"risk":"HIGH","reason":"Brand impersonation detected.","advice":"Block.","site":"fake.example","type":"brand_impersonation"}
            """.trimIndent()
        )
        val judge = RemoteJudge(
            baseUrl = serverUrl(),
            executor = sameThreadExecutor(),
        )

        val verdict = judgeNow(judge, "https://fake.example")

        assertEquals(Risk.HIGH, verdict.risk)
        assertEquals(Confidence.CERTAIN, verdict.confidence)
        assertEquals("Brand impersonation detected.", verdict.reason)
        assertEquals("Block.", verdict.advice)
        assertEquals("fake.example", verdict.site)
        assertEquals("brand_impersonation", verdict.type)
        assertEquals("remote", verdict.source)
    }

    @Test
    fun `returns unknown when remote call fails`() {
        startServer(status = 500, body = "server error")
        val judge = RemoteJudge(
            baseUrl = serverUrl(),
            executor = sameThreadExecutor(),
        )

        val verdict = judgeNow(judge, "https://example.com")

        assertEquals(Risk.UNKNOWN, verdict.risk)
        assertEquals(Confidence.UNCERTAIN, verdict.confidence)
        assertEquals("remote", verdict.source)
    }

    private fun startServer(status: Int, body: String) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/judge") { exchange ->
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
        responseHeaders.add("Content-Type", "application/x-ndjson")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun sameThreadExecutor() = java.util.concurrent.Executor { command -> command.run() }

    private fun judgeNow(judge: Judge, url: String): Verdict {
        val latch = CountDownLatch(1)
        lateinit var verdict: Verdict
        judge.judge(url) {
            verdict = it
            latch.countDown()
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        return verdict
    }
}
