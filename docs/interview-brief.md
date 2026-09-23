# Interview Brief

> GUADIAN On-device Judge를 면접에서 설명하기 위한 요약 문서.

## One-liner

광고로 유입되는 위험 URL을 기기 내부에서 먼저 판정하고, 불확실한 경우에만 remote fallback을 사용하는 local-first 하이브리드 판정 파이프라인입니다.

## Problem

- 광고 클릭 이후 도착 URL이 피싱, 사칭, APK 설치, 개인정보 입력 흐름으로 이어질 수 있음
- 모든 URL을 서버로 보내면 지연, 비용, 개인정보 전송 문제가 생김
- 반대로 온디바이스만으로 모든 케이스를 확정하기는 어려움

## Approach

```text
Local Verdict Store
  -> Rule Engine
  -> On-device Model
  -> Local RAG
  -> Remote Fallback
```

로컬에서 확정 가능한 케이스는 빠르게 처리하고, 불확실한 케이스만 다음 계층으로 넘깁니다.

## Architecture

| Layer | Role |
| :-- | :-- |
| `LocalRiskStore` | 도메인 단위 verdict 캐시 |
| `RuleBasedJudge` | APK, 공식 도메인, 브랜드 사칭, 의심 키워드 판정 |
| `OnDeviceModelJudge` | URL feature 기반 nearest-centroid MVP 모델 추론 |
| `LocalRagJudge` | 로컬 정책 스니펫 기반 보조 판정 |
| `RemoteJudge` | 로컬 판정 실패 시 FastAPI fallback 호출 |
| `HybridJudge` | 전체 판정 순서 제어 |

## Implementation Highlights

- `Verdict`에 `risk`, `reason`, `advice`, `source`, `confidence`, `type`을 포함해 설명 가능한 판정 결과를 반환
- 직접 만든 `url_risk_centroid_model.json`을 Android `OnDeviceModelJudge`에 연결
- `LocalRagJudge`가 근거만 반환하지 않고 `MEDIUM`/`HIGH` verdict까지 반환하도록 개선
- FastAPI 기반 remote fallback 서버와 `/health`, `/judge` endpoint 구현
- `HybridJudgeTest`로 store, rule, model, RAG, remote fallback 순서를 검증

## Model Evaluation

현재 모델은 seed fixture 기준 nearest-centroid classifier입니다.

| Metric | Result |
| :-- | :-- |
| Model accuracy | 91.67% |
| Baseline accuracy | 83.33% |
| HIGH precision | 100.00% |
| HIGH recall | 83.33% |

평가 리포트에는 label별 precision/recall과 confusion matrix를 포함했습니다.

## My Focus

- 기존 GUADIAN 판정 구조를 온디바이스 우선 구조로 재설계
- Kotlin 판정 파이프라인 구현
- Python 모델 학습/평가 파이프라인 정리
- Remote fallback 서버 테스트 가능하게 구성
- README, architecture, model, server 문서 구조화

## Trade-offs

| Decision | Reason | Trade-off |
| :-- | :-- | :-- |
| Local-first hybrid | 지연과 개인정보 전송 최소화 | 로컬 계층 설계 복잡도 증가 |
| Rule before model | 명확한 신호는 빠르고 설명 가능하게 처리 | 룰 유지보수 필요 |
| MVP nearest-centroid model | 가볍고 Android에 빠르게 연결 가능 | TFLite/ONNX 대비 표현력 제한 |
| Remote fallback | 로컬 불확실 케이스 보완 | 서버/API key/배포 관리 필요 |

## Current Limitations

- 실제 Android UI/Shield 연결은 아직 없음
- Azure 실제 배포는 준비 문서만 있음
- 모델 데이터셋은 seed fixture 수준
- TFLite/ONNX 런타임 전환 전 단계

## Next Steps

- Android `Verdict -> Shield UI` 연결
- Azure App Service 배포
- URL 데이터셋 확장과 라벨 품질 개선
- 모델 런타임을 assets JSON, TFLite, ONNX 중 하나로 고도화
- 실기기 end-to-end 테스트

## Files To Mention

| File | Why it matters |
| :-- | :-- |
| `HybridJudge.kt` | 전체 판정 파이프라인 제어 |
| `OnDeviceModelJudge.kt` | 직접 만든 MVP 모델을 Android 런타임에 연결 |
| `LocalRagJudge.kt` | 로컬 정책 근거 기반 판정 |
| `RemoteJudge.kt` | remote fallback 호출 |
| `server/main.py` | FastAPI fallback endpoint |
| `model/training/train_model.py` | 모델 학습과 평가 리포트 생성 |
| `HybridJudgeTest.kt` | 전체 로컬 판정 흐름 검증 |
