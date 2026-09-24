package com.guardian.ondevice.knowledge

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Judge
import com.guardian.ondevice.judge.Risk
import com.guardian.ondevice.judge.Verdict

class LocalKnowledgeJudge(
    private val retriever: LocalKnowledgeRetriever = LocalKnowledgeRetriever(),
) : Judge {
    override fun judge(url: String, done: (Verdict) -> Unit) {
        val snippets = retriever.retrieve(url)
        if (snippets.isEmpty()) {
            done(
                Verdict(
                    risk = Risk.UNKNOWN,
                    reason = "No local knowledge matched the URL.",
                    advice = "Use remote fallback if local judgment remains uncertain.",
                    source = "local-knowledge",
                    confidence = Confidence.UNCERTAIN,
                )
            )
            return
        }

        val evidence = snippets.joinToString { it.id }
        val primary = snippets.first()
        done(
            Verdict(
                risk = primary.risk,
                reason = "Local knowledge matched: $evidence.",
                advice = primary.advice,
                source = "local-knowledge",
                confidence = primary.confidence,
                type = primary.id,
            )
        )
    }
}
