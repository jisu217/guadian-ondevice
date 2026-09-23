# Data Collection Plan

> Expansion plan for safe, reviewed URL risk samples.

`model/data/sample_urls.csv`는 commit 가능한 seed fixture다. 실제 성능 주장을 위한 production dataset이 아니라 feature, training, pipeline 검증용이다.

## Current Seed Mix

- `synthetic` — 통제된 feature/pipeline 테스트
- `reference-derived` — GUADIAN 위험 카테고리 기반 재구성 샘플
- `public-benign` — LOW 라벨용 공개 공식/정상 URL

## Expansion Targets

| Stage | Target | Purpose |
| :-- | :-- | :-- |
| Seed | 120 samples, 30 per label | 첫 balanced training 실험 |
| Prototype | 400 samples, 100 per label | baseline과 trained model 비교 |
| Realistic | 1000+ reviewed samples | 일반화 성능과 edge case 평가 |

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
