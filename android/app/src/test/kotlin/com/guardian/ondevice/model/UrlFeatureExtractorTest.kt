package com.guardian.ondevice.model

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
    fun `parses url without scheme`() {
        val features = extractor.extract("support.example.com/help").values

        assertEquals(1.0, features.getValue("official_domain_match"))
        assertEquals(5.0, features.getValue("path_length"))
    }
}
