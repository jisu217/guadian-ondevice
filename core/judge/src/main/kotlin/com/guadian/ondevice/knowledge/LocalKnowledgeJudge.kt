package com.guadian.ondevice.knowledge

import com.guadian.ondevice.judge.Confidence
import com.guadian.ondevice.judge.Judge
import com.guadian.ondevice.judge.Layer
import com.guadian.ondevice.judge.ReasonCode
import com.guadian.ondevice.judge.Risk
import com.guadian.ondevice.judge.Verdict
import com.guadian.ondevice.url.UrlHost

class LocalKnowledgeJudge(
    private val retriever: LocalKnowledgeRetriever = LocalKnowledgeRetriever(),
) : Judge {
    override suspend fun judge(url: String): Verdict {
        val snippets = retriever.retrieve(url)
        val primary = snippets.firstOrNull()
            ?: return Verdict(
                risk = Risk.UNKNOWN,
                confidence = Confidence.UNCERTAIN,
                layer = Layer.KNOWLEDGE,
                reason = ReasonCode.KNOWLEDGE_NO_MATCH,
            )

        return Verdict(
            risk = primary.risk,
            confidence = primary.confidence,
            layer = Layer.KNOWLEDGE,
            reason = ReasonCode.KNOWLEDGE_MATCH,
            category = primary.category,
            host = UrlHost.of(url),
            detail = snippets.joinToString { it.id },
        )
    }
}
