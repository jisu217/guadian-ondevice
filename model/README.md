# Model Workspace

> Lightweight URL risk model workspace for Android on-device inference.

`model/`은 URL 위험 분류 모델의 데이터, feature, 학습, 평가, 산출물을 관리한다. Android 런타임 추론은 `android/` 모듈이 담당한다.

## Structure

| Path | Role |
| :-- | :-- |
| `data/` | 학습/테스트용 URL fixture |
| `docs/` | 데이터 스키마, 수집 계획, feature 설계 |
| `training/` | feature 추출, baseline, 학습 스크립트 |
| `evaluation/` | baseline/model 평가 리포트 |
| `export/` | 모바일 런타임 변환 결과를 둘 자리 |
| `artifacts/` | 생성된 모델 산출물 |

## Current MVP

```text
model/artifacts/url_risk_centroid_model.json
  -> nearest-centroid parameters embedded in OnDeviceModelJudge
  -> UrlFeatureExtractor builds the runtime feature vector
  -> OnDeviceModelJudge returns LOW/MEDIUM/HIGH when confident, otherwise UNKNOWN
```

현재 모델은 fine-tuned LLM이 아니라 URL feature 기반 nearest-centroid classifier다.

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

| Task | Command |
| :-- | :-- |
| Training tests | `python3 -m unittest discover model/training` |
| Baseline report | `python3 model/training/train_baseline.py` |
| Train model | `python3 model/training/train_model.py` |

## Data Policy

| Rule | Reason |
| :-- | :-- |
| Commit only sanitized fixtures | 저장소 공개/공유 안전성 |
| Do not commit raw logs or private URLs | 사용자 프라이버시 보호 |
| Redact tokens and identifiers | API key, session, 개인정보 누출 방지 |
| Keep large datasets outside repo | 저장소 경량화 |

## Next

| Work | Goal |
| :-- | :-- |
| Dataset expansion | 실제 일반화 성능 개선 |
| Asset-loaded JSON | Kotlin 코드 내장 값을 파일 로딩으로 분리 |
| TFLite/ONNX export | 표준 온디바이스 ML 런타임으로 전환 |
