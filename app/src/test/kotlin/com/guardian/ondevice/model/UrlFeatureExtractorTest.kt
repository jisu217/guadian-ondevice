package com.guardian.ondevice.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlFeatureExtractorTest {
    private val extractor = UrlFeatureExtractor()

    @Test
    fun `extracts apk risk features`() {
        val features = extractor.extract("https://unknown-site.test/download/app.apk").values

        assertEquals(1.0, features.getValue("has_apk"))
        assertEquals(1.0, features.getValue("suspicious_tld"))
        assertEquals(0.0, features.getValue("official_domain_match"))
    }

    @Test
    fun `extracts login brand features`() {
        val features = extractor.extract("https://bank-login-verification.test/account").values

        assertEquals(1.0, features.getValue("has_login"))
        assertEquals(1.0, features.getValue("brand_keyword_present"))
    }

    @Test
    fun `matches official domain`() {
        val features = extractor.extract("https://support.example.com/help").values

        assertEquals(1.0, features.getValue("official_domain_match"))
        assertTrue(features.getValue("subdomain_count") > 0.0)
    }

    @Test
    fun `returns vector in stable feature order`() {
        val vector = extractor.extract("https://promo-reward-event.test/free-gift").vector()

        assertEquals(UrlFeatureExtractor.FEATURE_NAMES.size, vector.size)
        assertTrue(vector.all { it >= 0.0 })
    }

    @Test
    fun `extracts redirect and ad network features`() {
        val features = extractor.extract(
            "https://googleads.g.doubleclick.net/pcs/click?" +
                "adurl=https%3A%2F%2Fexample.com%2Flanding&gclid=abc123&utm_source=display"
        ).values

        assertEquals(3.0, features.getValue("query_param_count"))
        assertEquals(1.0, features.getValue("utm_param_count"))
        assertEquals(2.0, features.getValue("tracking_param_count"))
        assertEquals(1.0, features.getValue("has_nested_url"))
        assertEquals(1.0, features.getValue("has_encoded_url"))
        assertEquals(1.0, features.getValue("has_ad_click_host"))
    }

    @Test
    fun `extracts base64-like redirect token`() {
        val features = extractor.extract(
            "https://s.adscope.co.kr/adt_connect.php?" +
                "u=aHR0cDovL3Nha3VuemEuY29tL25ld3NtL2luZGV4X2JuLmh0bWw&v=&d="
        ).values

        assertEquals(1.0, features.getValue("has_ad_click_host"))
        assertEquals(1.0, features.getValue("has_base64_like_token"))
    }

    @Test
    fun `matches python feature parity fixture`() {
        val rows = readFeatureParityRows()

        assertTrue(rows.isNotEmpty())
        for ((url, expected) in rows) {
            val actual = extractor.extract(url).vector()

            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEachIndexed { index, (left, right) ->
                assertClose(left, right, "Feature ${UrlFeatureExtractor.FEATURE_NAMES[index]} drifted for $url")
            }
        }
    }

    @Test
    fun `parses url without scheme`() {
        val features = extractor.extract("support.example.com/help").values

        assertEquals(1.0, features.getValue("official_domain_match"))
        assertEquals(5.0, features.getValue("path_length"))
    }

    private fun readFeatureParityRows(): List<Pair<String, List<Double>>> {
        val stream = javaClass.getResourceAsStream("/feature_parity_cases.csv")
            ?: error("feature_parity_cases.csv resource is missing")
        val lines = stream.bufferedReader().readLines().filter { it.isNotBlank() }
        val header = lines.first().split(",")

        assertEquals(listOf("url") + UrlFeatureExtractor.FEATURE_NAMES, header)
        return lines.drop(1).map { line ->
            val columns = line.split(",")
            columns.first() to columns.drop(1).map { it.toDouble() }
        }
    }

    private fun assertClose(expected: Double, actual: Double, message: String) {
        assertTrue(abs(expected - actual) < 1.0e-12, "$message: expected $expected but was $actual")
    }
}
