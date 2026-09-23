# GUADIAN On-device Judge

> A hybrid risk judgment pipeline that evaluates ad-driven URLs locally first.

본 저장소는 SKT FLY AI 부트캠프에서 개발한 GUADIAN 원본 프로젝트의 판정 구조를 기반으로, 온디바이스 우선 하이브리드 판정 파이프라인을 재설계한 연구용 프로젝트다.

## Overview

URL 위험 판정을 기기 내부에서 먼저 수행하고, 불확실한 경우에만 remote fallback을 사용한다.

- **Local-first** — 캐시, 룰, 모델, RAG를 로컬에서 우선 실행
- **Low latency** — 네트워크 없이 확정 가능한 케이스를 빠르게 반환
- **Privacy-aware** — 원격으로 전송되는 URL 범위 최소화
- **Explainable** — `reason`, `advice`, `source`를 포함한 Verdict 반환
- **Fallback-aware** — 로컬 판정이 불확실할 때만 remote fallback 호출

## Judgment Pipeline

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

| 단계 | 구현 | 역할 |
| :-- | :-- | :-- |
| Local Verdict Store | `LocalRiskStore` | 도메인 단위 캐시 |
| Rule Engine | `RuleBasedJudge`, `rules/` | 명시적 위험 신호 판정 |
| On-device Model | `OnDeviceModelJudge` | nearest-centroid MVP 모델 추론 |
| Local RAG | `LocalRagJudge` | 로컬 정책 근거 기반 판정 |
| Remote Fallback | `RemoteJudge`, `server/` | FastAPI 기반 원격 보조 판정 |

`HybridJudge`가 전체 순서를 제어하고, `LocalReasoningJudge`가 `OnDeviceModelJudge -> LocalRagJudge` 순서의 로컬 추론 계층을 실행한다.

## Current Status

- [x] Local Verdict Store
- [x] Rule Engine
- [x] On-device Model (MVP)
- [x] Local RAG (MVP)
- [x] Remote Fallback Server
- [x] HybridJudge pipeline test
- [ ] Android UI/Shield 연결
- [ ] Azure 실제 배포
- [ ] TFLite/ONNX 전환

상세 상태는 [`docs/status.md`](./docs/status.md)에서 관리한다.

## Repository Structure

```text
guadian-ondevice/
  android/app/src/main/kotlin/com/guardian/ondevice/
    judge/     HybridJudge, RuleBasedJudge, RemoteJudge, Verdict
    rules/     APK, 사칭, 의심 URL 등 룰
    store/     LocalRiskStore
    model/     OnDeviceModelJudge, UrlFeatureExtractor
    rag/       LocalRagJudge, LocalKnowledgeRetriever

  server/      FastAPI remote fallback server
  model/       training, evaluation, artifacts
  docs/        architecture, rule policy, status
  reference/   GUADIAN 원본 프로젝트 기반 참조 자료
```

## Model

```text
model/artifacts/url_risk_centroid_model.json
  -> Kotlin nearest-centroid runtime in OnDeviceModelJudge
  -> UrlFeatureExtractor feature vector
  -> LOW / MEDIUM / HIGH / UNKNOWN Verdict
```

현재는 직접 만든 nearest-centroid 모델을 Kotlin 런타임에 연결한 MVP 단계다. 추후 assets JSON, TFLite, ONNX 중 하나로 교체할 수 있다.

## Remote Fallback Server

```bash
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
cp server/.env.example server/.env
cd server
../.venv/bin/uvicorn main:app --reload
```

환경변수:

- `ANTHROPIC_API_KEY` — Claude remote fallback 호출용 API key
- `ANTHROPIC_MODEL` — Claude 모델명. 기본값은 `claude-3-5-haiku-latest`

로컬 실행, 테스트, Azure 준비는 [`server/README.md`](./server/README.md)를 기준으로 한다.

## Test

```bash
./gradlew test
cd server
../.venv/bin/python -m unittest discover .
```

`HybridJudgeTest`는 Local Store, Rule Engine, On-device Model, Local RAG, Remote Fallback이 정해진 순서로 연결되는지 검증한다.

## Documentation

- [`docs/architecture.md`](./docs/architecture.md) — 온디바이스 우선 하이브리드 판정 계층과 fallback 규칙
- [`docs/rule-policy.md`](./docs/rule-policy.md) — 룰 기반 판정 출력, 카테고리, 설계 기준
- [`docs/status.md`](./docs/status.md) — 현재 구현 완료 범위와 남은 작업
- [`model/README.md`](./model/README.md) — 모델 학습, 평가, 변환, 산출물 관리
- [`server/README.md`](./server/README.md) — Remote Fallback 서버 실행, 환경변수, Azure 준비
- [`reference/README.md`](./reference/README.md) — GUADIAN 원본 프로젝트 기반 참조 파일과 원본 경로 매핑
