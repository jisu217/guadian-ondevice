# Model Workspace

> Lightweight URL risk model workspace for Android on-device inference.

`model/`은 URL 위험 분류 모델의 데이터, feature, 학습, 평가, 산출물을 관리한다. Android 런타임 추론은 `android/` 모듈이 담당한다.

## Structure

- `data/` — 학습/테스트용 URL fixture, `official_domains.txt`(Tranco 상위 5만 + 실제 브랜드 도메인 — Python·Kotlin이 같은 파일을 읽는다)
- `docs/` — 데이터 스키마, 수집 계획, feature 설계
- `training/` — feature 추출, baseline, 학습 스크립트
- `evaluation/` — baseline/model 평가 리포트
- `export/` — 모바일 런타임 변환 결과를 둘 자리
- `artifacts/` — 생성된 모델 산출물

## Current MVP

```text
model/artifacts/url_risk_centroid_model.json
  -> nearest-centroid parameters embedded in OnDeviceModelJudge
  -> UrlFeatureExtractor builds the runtime feature vector
  -> OnDeviceModelJudge returns LOW/MEDIUM/HIGH when confident, otherwise UNKNOWN
```

현재 모델은 fine-tuned LLM이 아니라 URL feature 기반 nearest-centroid classifier다.

`sample_urls.csv`는 이제 648행 — 합성 400개 + 실제 광고 도착지 248개(`reference/gold_251.jsonl`, 3-LLM 합의 + 사람 검토). 실데이터를 섞자 model accuracy가 97.50% → 72.66%로 떨어졌고, 원인은 URL 문자열만으론 안 보이는 페이지 신호·global threshold의 단일 오탐 취약성·min-max scaling의 이상치 민감성 세 가지였다. 이 중 threshold는 라벨별로 분리 보정해서 고쳤다 — HIGH의 confident 판정이 0건 → 92건으로 회복됐다. MEDIUM은 자기 threshold를 줘도 여전히 0건이라, 이건 threshold 문제가 아니라 URL-only 설계 자체의 한계로 실험상 분리 확인됐다. 자세한 내용과 남은 개선 후보(min-max scaling 교체 등)는 [`docs/interview-brief.md`](../docs/interview-brief.md)의 Model Evaluation, [`trained-model-report.md`](evaluation/trained-model-report.md)를 참고.

## Workflow

```text
Prepare dataset
  -> extract deterministic URL features
  -> train lightweight classifier
  -> evaluate accuracy and errors
  -> export mobile-ready artifact
  -> connect from OnDeviceModelJudge
```

## Commands

- Training tests — `python3 -m unittest discover model/training`
- Baseline report — `python3 model/training/train_baseline.py`
- Train model — `python3 model/training/train_model.py`
- Rebuild official domain allowlist — `python3 model/training/build_official_domains.py --tranco /path/to/top-1m.csv` (download from [tranco-list.eu](https://tranco-list.eu); regenerates `data/official_domains.txt`, which Python and Kotlin both read directly)

## Data Policy

- **Commit only sanitized fixtures** — 저장소 공개/공유 안전성. `reference-derived` 실데이터 행은 URL과 라벨만 담고, 원본 페이지 본문(개인 이메일 등)은 포함하지 않는다 — `reference/README.md`의 출처 기록 참고
- **Do not commit raw logs or private URLs** — 사용자 프라이버시 보호
- **Redact tokens and identifiers** — API key, session, 개인정보 누출 방지
- **Keep large datasets outside repo** — 저장소 경량화. `official_domains.txt`(약 650KB)는 예외로 커밋하지만, 그 원본인 Tranco top-1m.csv(수십MB)는 커밋하지 않는다
