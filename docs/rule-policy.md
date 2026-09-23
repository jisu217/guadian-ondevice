# Rule Policy

> Deterministic local signals before model or remote fallback.

`RuleBasedJudge`는 빠르고 설명 가능한 위험 신호를 먼저 처리한다. 룰이 확정하지 못한 입력만 모델, RAG, remote fallback으로 넘어간다.

## Output Contract

| Field | Description |
| :-- | :-- |
| `risk` | `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN` |
| `reason` | 판정 사유 |
| `advice` | 사용자 권장 행동 |
| `source` | 판정 출처 |
| `confidence` | `CERTAIN` 또는 `UNCERTAIN` |

## Categories

| Category | Example | Risk |
| :-- | :-- | :-- |
| Trusted domain | 공식 도메인 또는 허용 목록 도메인 | `LOW` |
| APK download | 외부 APK 다운로드 URL | `HIGH` |
| Brand impersonation | 브랜드 키워드와 비공식 도메인 조합 | `HIGH` |
| Suspicious pattern | 이벤트, 선물, 계정 확인 등 의심 키워드 | `MEDIUM` |
| No signal | 명확한 룰 매칭 없음 | `UNKNOWN` |

## Current Rules

| Rule | Source | Signal |
| :-- | :-- | :-- |
| APK | `ApkRule` | `.apk`, install/download 흐름 |
| Official domain | `BrandImpersonationRule` | 공식 도메인 매칭 |
| Brand impersonation | `BrandImpersonationRule` | 브랜드명 포함 비공식 host |
| Suspicious URL | `SuspiciousUrlRule` | free, gift, event, loan, verify, account |

## Design Notes

| Guideline | Reason |
| :-- | :-- |
| Prefer explicit signals | 오탐을 줄이고 설명 가능성 유지 |
| Keep rules cheap | 온디바이스 선행 계층이므로 빠르게 실행 |
| Return `UNKNOWN` when uncertain | 불확실한 입력은 모델/RAG/fallback으로 넘김 |
| Keep rules testable | 입력 URL과 기대 verdict로 검증 가능 |
