# Data Collection Plan

> Expansion plan for safe, reviewed URL risk samples.

`model/data/sample_urls.csv`는 commit 가능한 seed fixture다. 실제 성능 주장을 위한 production dataset이 아니라 feature, training, pipeline 검증용이다.

## Current Seed Mix

- `synthetic` — 통제된 feature/pipeline 테스트
- `reference-derived` — GUADIAN 위험 카테고리 기반 재구성 샘플
- `public-benign` — LOW 라벨용 공개 공식/정상 URL

## Expansion Targets

| Stage | Target | Purpose | Status |
| :-- | :-- | :-- | :-- |
| Seed | 120 samples, 30 per label | 첫 balanced training 실험 | Done |
| Prototype | 400 samples, 100 per label | baseline과 trained model 비교 | Done |
| Realistic | 1000+ reviewed samples | 일반화 성능과 edge case 평가 | In progress (648) |

Prototype 단계(400개)는 `synthetic`/`reference-derived`/`public-benign` 조합만 썼다. Realistic 단계는 여기에 `guadian23-model-compare-main/gold/gold_251.jsonl` — 실제 광고 도착지 251건 중 248건(깨진 URL 2, 중복 1 제외)을 `reference-derived`로 병합해 시작했다(2026-09-24). 라벨 출처(3사 LLM 합의 vs 사람 검토)는 `reviewed_by`에 행 단위로 남긴다 — 251건 중 220건이 LLM 합의였다는 걸 숨기지 않는다.

실데이터를 섞은 결과 모델 accuracy가 97.50% → 72.66%로 떨어졌다. 이건 실패가 아니라 발견이다 — `docs/interview-brief.md`의 Model Evaluation 항목에 원인 셋(URL-only 설계의 정보 한계, global threshold의 단일 오탐 취약성, min-max scaling의 이상치 취약성)을 정리했다. HIGH 라벨은 실데이터에서 251건 중 1건뿐이라(실제 광고 트래픽은 노골적 피싱을 거의 사전 차단하기 때문) 여전히 synthetic 데이터에 의존한다.

## Collection Rules

- **Keep data sanitized** — 저장소에 안전하게 commit 가능해야 함
- **No raw user data** — browsing history, production logs, private URLs 금지
- **Redact identifiers** — token, session, phone, account 정보 제거
- **Verify public feeds** — license와 안전성을 확인하고 raw active malicious URL commit 금지
- **Add useful variety** — 중복보다 새로운 패턴과 label balance 우선

## Review Checklist

- [ ] Label is valid — `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN`
- [ ] Metadata is filled — `source`, `reason`, `category`
- [ ] No duplicate pattern — 기존 row와 near-duplicate 아님
- [ ] Query is sanitized — identifier 가능성 있으면 redacted
- [ ] Adds value — 새 패턴 또는 label balance 개선
