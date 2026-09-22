# On-device-first Hybrid Architecture

GUADIAN On-device Judge는 URL 위험 판정을 기기 내부에서 우선 수행하고, 불확실한 경우에만 원격 fallback을 사용하는 온디바이스 우선 하이브리드 판정 구조다.

명확한 위험 신호는 로컬에서 즉시 처리하고, 불확실한 입력만 모델, RAG, remote fallback 계층으로 넘긴다. 따라서 이 구조는 모든 판정을 로컬에서만 처리하는 on-device only 방식이 아니라, local-first 전략을 중심에 둔 hybrid 방식이다.

## Target Flow

```text
URL Input
  ↓
Local Verdict Store
  ↓ miss
Rule Engine
  ↓ uncertain
On-device Model / Local RAG
  ↓ low confidence
Remote Fallback
  ↓
Persist Verdict
  ↓
Shield UI Mapping
```

## MVP Flow

```text
URL Input
  ↓
LocalRiskStore
  ↓ miss
RuleBasedJudge
  ↓ uncertain
RemoteJudge
  ↓
Persist Verdict
  ↓
Shield UI
```

## Layers

| Layer | Role |
| :-- | :-- |
| LocalRiskStore | URL 또는 도메인 단위의 판정 결과를 조회하고 저장 |
| RuleBasedJudge | APK, 공식 도메인, 사칭 가능성, 의심 URL 패턴 등 명시적 신호를 판정 |
| OnDeviceModelJudge | 룰로 판단하기 어려운 URL을 경량 모델로 분류 |
| LocalKnowledgeRetriever | 로컬 정책 스니펫과 위험 패턴을 검색해 판정 근거 후보를 반환 |
| LocalRagJudge | 검색된 로컬 근거를 Verdict 흐름에 연결 |
| RemoteJudge | 로컬 판정이 불확실한 경우 원격 API로 보조 판정 |
| HybridJudge | 각 판정 계층을 순서대로 실행하고 최종 Verdict를 결정 |

## Remote Fallback Server

`server/`는 `RemoteJudge`가 호출하는 `/judge` API의 최소 구현이다.

| Component | Role |
| :-- | :-- |
| `server/main.py` | `/health`, `/judge` FastAPI 엔드포인트 |
| `server/judge_service.py` | 로컬 서버 precheck, Claude 호출, Verdict 변환 |
| `server/.env.example` | `ANTHROPIC_API_KEY`, 모델명 등 서버 환경변수 예시 |

Claude API key는 Android 모듈에 포함하지 않고 서버 환경변수로만 관리한다.

## Design Principles

| Principle | Description |
| :-- | :-- |
| Local-first | 저장된 판정 결과와 명확한 위험 신호를 기기 내부에서 우선 처리 |
| Explainable | 룰 기반 판정은 위험 사유와 사용자 권장 행동을 함께 반환 |
| Minimal remote use | 원격 판정은 로컬 판단이 불확실한 경우에만 사용 |
| Replaceable layers | 룰, 모델, RAG, remote fallback 계층을 독립적으로 교체 가능하게 유지 |
| Persisted learning | 최종 Verdict를 저장해 반복 입력의 판정 비용을 줄임 |

## Verdict States

| State | Meaning |
| :-- | :-- |
| LOW | 정상 이동 가능 |
| MEDIUM | 사용자 재확인이 필요한 의심 흐름 |
| HIGH | 차단 또는 강한 경고가 필요한 위험 흐름 |
| UNKNOWN | 판정 근거가 부족하거나 fallback 실패 |

## Fallback Rules

| Condition | Action |
| :-- | :-- |
| Cached verdict exists | 저장된 Verdict를 즉시 반환 |
| Clear local signal exists | RuleBasedJudge 결과를 반환 |
| Local signal is uncertain | 모델 또는 로컬 지식 검색으로 보강 |
| Confidence is still low | 원격 fallback 사용 |
| Remote fallback fails | UNKNOWN 반환 |
