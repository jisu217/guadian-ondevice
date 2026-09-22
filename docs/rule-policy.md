# Rule Policy

RuleBasedJudge는 모델이나 원격 fallback을 호출하기 전에 실행되는 결정적 위험 신호 계층이다.

룰은 빠르게 계산할 수 있어야 하며, 판정 결과에는 위험도뿐 아니라 사용자가 이해할 수 있는 사유와 권장 행동이 포함되어야 한다.

## Rule Output

| Field | Description |
| :-- | :-- |
| risk | LOW, MEDIUM, HIGH, UNKNOWN 중 하나 |
| reason | 어떤 신호 때문에 해당 위험도로 판정했는지 설명 |
| advice | 사용자에게 보여줄 권장 행동 |
| source | 판정 출처. 예: local-store, rule, model, rag, remote |
| confidence | CERTAIN 또는 UNCERTAIN |

## Rule Categories

| Category | Example | Expected Risk |
| :-- | :-- | :-- |
| Trusted domain | 공식 도메인 또는 허용 목록 도메인 | LOW |
| APK download | 신뢰되지 않은 출처의 APK 다운로드 URL | HIGH |
| Brand impersonation | 브랜드 키워드와 비공식 도메인의 조합 | MEDIUM 또는 HIGH |
| Suspicious pattern | 과도한 리다이렉트, 단축 URL, 의심 파라미터 | MEDIUM |
| No signal | 명확한 룰 매칭 없음 | UNKNOWN |

## Policy Examples

```text
APK download outside trusted stores
  → HIGH

Known official domain
  → LOW

Brand keyword with non-official domain
  → HIGH or MEDIUM

Suspicious landing URL pattern
  → MEDIUM

No deterministic signal
  → UNKNOWN / UNCERTAIN
```

## Rule Design Notes

| Guideline | Reason |
| :-- | :-- |
| Prefer explicit signals | 온디바이스 룰은 빠르고 설명 가능해야 함 |
| Avoid broad keyword-only blocking | 정상 도메인의 오탐을 줄이기 위함 |
| Return UNKNOWN when uncertain | 불확실한 입력은 모델, RAG, fallback 계층으로 넘김 |
| Keep rules testable | 각 룰은 입력 URL과 기대 Verdict로 검증 가능해야 함 |
