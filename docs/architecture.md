# Architecture

> Local-first hybrid judgment flow for URL risk decisions.

GUADIAN On-device Judge는 로컬 판정을 먼저 수행하고, 불확실한 경우에만 remote fallback을 호출한다.

## Flow

```text
URL Input
  ↓
Local Verdict Store
  ↓ miss
Rule Engine
  ↓ uncertain
On-device Model
  ↓ low confidence / unknown
Local RAG
  ↓ still low confidence
Remote Fallback
  ↓
Verdict
```

## Runtime Composition

```text
HybridJudge
  ├─ LocalRiskStore
  ├─ RuleBasedJudge
  ├─ LocalReasoningJudge
  │   ├─ OnDeviceModelJudge
  │   └─ LocalRagJudge
  └─ RemoteJudge
```

## Layers

| Layer | Implementation | Role |
| :-- | :-- | :-- |
| Local Store | `LocalRiskStore` | 도메인 단위 verdict 캐시 |
| Rule Engine | `RuleBasedJudge`, `rules/` | 명시적 위험 신호 판정 |
| Local Reasoning | `LocalReasoningJudge` | 모델과 RAG 순서 제어 |
| On-device Model | `OnDeviceModelJudge` | nearest-centroid MVP 모델 추론 |
| Local RAG | `LocalRagJudge` | 로컬 정책 근거 기반 판정 |
| Remote Fallback | `RemoteJudge`, `server/` | 로컬 불확실 케이스 보조 판정 |

## Verdict

- `LOW` — 정상 이동 가능
- `MEDIUM` — 사용자 재확인이 필요한 의심 흐름
- `HIGH` — 차단 또는 강한 경고가 필요한 위험 흐름
- `UNKNOWN` — 판정 근거 부족 또는 fallback 실패

## Fallback Rules

1. Cached verdict exists → `LocalRiskStore` verdict 반환
2. Clear rule signal exists → `RuleBasedJudge` verdict 반환
3. Rule is uncertain → `OnDeviceModelJudge` 실행
4. Model is uncertain → `LocalRagJudge` 실행
5. Local RAG is uncertain → `RemoteJudge` 실행
6. Remote fails → `UNKNOWN` 반환
