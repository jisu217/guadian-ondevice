package com.guadian.ondevice.llm

import com.guadian.ondevice.verdict.Evidence
import com.guadian.ondevice.verdict.VerdictTable.Facts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageFactsTest {
    private val pageFacts = PageFacts()

    private fun facts(obs: PageObservation) = pageFacts.evidence(obs, Evidence(Facts())).facts

    private fun page(finalUrl: String = "https://shop.example.com/", status: Int = 200, text: String = "",
                     forms: List<FormObservation> = emptyList(), apk: List<String> = emptyList()) =
        PageObservation(url = "https://ad.example/click", loaded = true, finalUrl = finalUrl, status = status,
            text = text, textChars = text.length, forms = forms, apkLinks = apk)

    private fun form(vararg asks: String) = FormObservation(asks.toList(), externalAction = false)

    @Test
    fun `the brand's own site is official after redirects, a platform where anyone publishes is not`() {
        assertTrue(facts(page(finalUrl = "https://www.naver.com/")).official!!)
        assertFalse(facts(page(finalUrl = "https://m.blog.naver.com/someone/1")).official!!)
        assertFalse(facts(page()).official!!)
    }

    @Test
    fun `an error status or a failed load is not opened`() {
        assertFalse(facts(page(status = 404)).opened!!)
        val failed = facts(PageObservation(url = "https://www.naver.com/", loaded = false))
        assertFalse(failed.opened!!)
        assertFalse(failed.official!!)
    }

    @Test
    fun `personal fields follow the labeling guide's rule 5`() {
        assertTrue(facts(page(forms = listOf(form("name", "phone")))).personalField!!)
        assertFalse(facts(page(forms = listOf(form("email")), text = "개인정보 수집 및 이용에 동의합니다")).personalField!!)
        assertTrue(facts(page(forms = listOf(form()), text = "개인정보 수집 및 이용에 동의합니다")).personalField!!)
        assertFalse(facts(page(forms = listOf(form()), text = "상담신청")).personalField!!)
        assertFalse(facts(page(text = "빠른 상담 신청 연락처를 남겨 주세요")).personalField!!)
    }

    @Test
    fun `registration or card fields and apk links`() {
        val f = facts(page(forms = listOf(form("password", "rrn")), apk = listOf("https://x.example/a.apk")))
        assertTrue(f.sensitiveField!!)
        assertTrue(f.apkLink!!)
        assertEquals(false, facts(page(forms = listOf(form("password")))).sensitiveField)
    }
}
