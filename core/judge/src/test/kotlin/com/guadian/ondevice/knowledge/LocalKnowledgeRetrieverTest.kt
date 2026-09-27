package com.guadian.ondevice.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalKnowledgeRetrieverTest {
    @Test
    fun `retrieves apk policy snippet`() {
        val retriever = LocalKnowledgeRetriever()

        val snippets = retriever.retrieve("https://example.com/download/app.apk")

        assertEquals("apk", snippets.first().id)
    }

    @Test
    fun `retrieves brand impersonation policy snippet`() {
        val retriever = LocalKnowledgeRetriever()

        val snippets = retriever.retrieve("naver login official domain mismatch")

        assertEquals("brand_impersonation", snippets.first().id)
    }

    @Test
    fun `returns empty result for unrelated query`() {
        val retriever = LocalKnowledgeRetriever()

        val snippets = retriever.retrieve("plain article reading page")

        assertTrue(snippets.isEmpty())
    }
}
