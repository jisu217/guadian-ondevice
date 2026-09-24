package com.guardian.ondevice.knowledge

import com.guardian.ondevice.judge.Confidence
import com.guardian.ondevice.judge.Risk

data class KnowledgeSnippet(
    val id: String,
    val title: String,
    val text: String,
    val keywords: Set<String> = emptySet(),
    val risk: Risk = Risk.UNKNOWN,
    val advice: String = "Use remote fallback if local judgment remains uncertain.",
    val confidence: Confidence = Confidence.UNCERTAIN,
)

class LocalKnowledgeRetriever(
    private val snippets: List<KnowledgeSnippet> = defaultSnippets(),
) {
    fun retrieve(query: String, limit: Int = DEFAULT_LIMIT): List<KnowledgeSnippet> {
        val queryTokens = query.tokens()
        if (queryTokens.isEmpty()) return emptyList()

        return snippets
            .map { snippet -> snippet to snippet.score(queryTokens) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<KnowledgeSnippet, Int>> { it.second }
                    .thenBy { it.first.id }
            )
            .take(limit)
            .map { (snippet, _) -> snippet }
    }

    private fun KnowledgeSnippet.score(queryTokens: Set<String>): Int {
        val searchableTokens = (title + " " + text).tokens()
        val keywordTokens = keywords.flatMap { it.tokens() }.toSet()

        val bodyMatches = queryTokens.count { it in searchableTokens }
        val keywordMatches = queryTokens.count { it in keywordTokens }
        return bodyMatches + keywordMatches * KEYWORD_WEIGHT
    }

    private fun String.tokens(): Set<String> {
        return lowercase()
            .split(Regex("""[^\p{L}\p{N}.]+"""))
            .map { it.trim() }
            .filter { it.length >= MIN_TOKEN_LENGTH }
            .toSet()
    }

    companion object {
        private const val DEFAULT_LIMIT = 3
        private const val KEYWORD_WEIGHT = 3
        private const val MIN_TOKEN_LENGTH = 2

        fun defaultSnippets(): List<KnowledgeSnippet> {
            return listOf(
                KnowledgeSnippet(
                    id = "apk",
                    title = "APK download risk",
                    text = "공식 앱스토어 외부의 APK 직접 다운로드는 기기 장악이나 악성 앱 설치로 이어질 수 있으므로 고위험 신호로 본다.",
                    keywords = setOf("apk", "download", "install", "app store", "앱", "설치"),
                    risk = Risk.HIGH,
                    advice = "Block the navigation before installation.",
                    confidence = Confidence.CERTAIN,
                ),
                KnowledgeSnippet(
                    id = "brand-impersonation",
                    title = "Brand impersonation",
                    text = "브랜드를 자처하지만 공식 도메인과 일치하지 않는 경우 사칭 가능성이 높은 위험 신호로 본다.",
                    keywords = setOf("brand", "impersonation", "official domain", "login", "사칭", "공식", "도메인"),
                    risk = Risk.HIGH,
                    advice = "Block or strongly warn before opening.",
                    confidence = Confidence.CERTAIN,
                ),
                KnowledgeSnippet(
                    id = "personal-info",
                    title = "Personal information request",
                    text = "이름, 전화번호, 주민번호, 카드번호 등 민감정보 입력을 요구하는 흐름은 사용자 재확인이 필요한 신호로 본다.",
                    keywords = setOf("personal info", "phone", "card", "password", "개인정보", "전화번호", "카드", "비밀번호"),
                    risk = Risk.MEDIUM,
                    advice = "Ask the user to confirm before entering personal information.",
                    confidence = Confidence.CERTAIN,
                ),
                KnowledgeSnippet(
                    id = "urgency",
                    title = "Urgency and reward lure",
                    text = "무료, 당첨, 한정, 이벤트처럼 사용자의 즉시 행동을 유도하는 문구는 의심 신호로 본다.",
                    keywords = setOf("free", "gift", "event", "limited", "무료", "당첨", "한정", "이벤트"),
                    risk = Risk.MEDIUM,
                    advice = "Ask the user to confirm before opening.",
                    confidence = Confidence.CERTAIN,
                ),
                KnowledgeSnippet(
                    id = "unknown",
                    title = "Unknown verdict handling",
                    text = "판정 근거가 부족하거나 원격 fallback이 실패한 경우 UNKNOWN으로 두고 사용자에게 안전 확인 흐름을 제공한다.",
                    keywords = setOf("unknown", "fallback", "uncertain", "확인", "실패", "불확실"),
                ),
            )
        }
    }
}
