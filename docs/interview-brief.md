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

현재 데이터셋은 648개 — 합성 400개(라벨당 100개) + 실제 광고 도착지 248개(`guadian23-model-compare-main`의 Claude/Gemini/GPT-5 3사 합의 + 사람 검토 gold set). 라벨 출처는 행 단위로 `reviewed_by`(human-review / llm-consensus)에 정직하게 남겼습니다.

| Metric | 합성 400개만 | 실데이터 248개 포함(648개) |
| :-- | --: | --: |
| Model accuracy | 97.50% | 72.66% |
| Model macro F1 | 97.53% | 74.24% |
| Baseline accuracy (같은 split) | 88.75% | 68.75% |

**정확도가 떨어진 게 핵심이 아니라, 왜 떨어졌는지가 핵심입니다.** 실데이터를 섞으니 세 가지가 구체적으로 드러났습니다.

1. **URL 문자열만으로는 못 보는 신호가 있다** — 실제 광고 도착지의 MEDIUM(개인정보 상담폼, JS 전용 렌더링, 확인불가 404)은 페이지 본문을 열어야 보이는 신호입니다. 원본 GUADIAN의 LLM judge는 `page_signals`로 이걸 봤지만, 온디바이스 모델은 URL만 보므로 원리적으로 접근 불가 — MEDIUM recall 51%, UNKNOWN precision 50%로 나타남.
2. **global threshold 하나가 전체를 좌우한다** — distance threshold가 4개 라벨에 공통 스칼라 하나였습니다. cross-validation에서 LOW/MEDIUM 경계의 애매한 케이스 단 1건(`m.fmkorea.com`, distance 0.35)이 걸리자, 원래 1.13이어야 할 threshold가 0.35로 떨어지면서 **HIGH·MEDIUM은 학습셋 전체에서 단 한 건도 "확신" 문턱을 못 넘게** 됐습니다.
3. **min-max scaling이 이상치에 취약하다** — 실데이터의 긴 트래킹 URL 몇 개가 `url_length` 등의 스케일 범위를 9~1675로 벌려놓으면서, 다른 모든 샘플의 상대 거리가 좁은 구간으로 눌립니다.

**2번은 실제로 고쳤습니다.** `calibrate_certain_distance_thresholds`가 라벨별로 따로 보정하도록 바꾸니(`train_model.py`), HIGH는 confident 케이스가 **0건 → 648건 중 92건**으로 회복됐습니다 — 원래 1.04였어야 할 threshold가 남의 경계 오류 때문에 0.35로 끌려 내려간 것뿐이었다는 게 정확히 확인된 셈입니다. 반면 **MEDIUM은 자기 라벨만의 threshold(0.79)를 줘도 여전히 confident 케이스가 0건**입니다 — 이건 threshold 문제가 아니라 1번(URL-only의 정보 한계) 때문이라는 걸 실험으로 분리해서 보여줍니다. 3번(min-max scaling)은 아직 손대지 않았습니다.

`OnDeviceModelJudgeTest`에도 이 결과를 그대로 남겼습니다 — HIGH는 다시 confident하게 판정하는 테스트로, MEDIUM은 nearest-centroid의 방향(label)은 맞지만 confidence가 기준을 못 넘겨 `UNKNOWN`으로 떨어지는 케이스로, 각각 명시적으로 검증합니다.

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
- 라벨별 threshold 보정 이후에도 MEDIUM은 URL만으로 confident하게 판정하지 못함(HIGH는 회복됨) — 이 경우 로컬 판정은 LocalRagJudge/RemoteJudge로 넘어가므로 안전하지만, 온디바이스 계층이 스스로 확정할 수 있는 범위는 LOW/HIGH/officially-listed 케이스에 가깝게 좁아짐
- TFLite/ONNX 런타임 전환 전 단계

## Next Steps

- Android `Verdict -> Shield UI` 연결
- Azure App Service 배포
- feature scaling을 min-max 대신 percentile clip 또는 log 변환으로 교체 (긴 URL 이상치에 대한 강건성)
- 페이지 신호(form 존재, JS-only 여부 등)를 온디바이스에서 확보할 방법 검토 — 안 되면 이 신호가 필요한 케이스는 구조적으로 RAG/원격에 맡기는 것으로 역할 재정의
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
