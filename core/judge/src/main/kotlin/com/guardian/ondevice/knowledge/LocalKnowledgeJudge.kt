package com.guardian.ondevice.knowledge

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Layer
import com.guardian.ondevice.judge.ReasonCode
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict
import com.guardian.ondevice.util.UrlHost

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
