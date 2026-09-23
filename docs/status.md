# Project Status

> MVP implementation status and remaining work.

## Completed

| Area | Status | Location |
| :-- | :-- | :-- |
| Local Verdict Store | Done | `LocalRiskStore` |
| Rule Engine | Done | `RuleBasedJudge`, `rules/` |
| On-device Model | MVP done | `OnDeviceModelJudge` |
| Local RAG | MVP done | `LocalRagJudge` |
| Remote Fallback | Done | `server/` |
| Hybrid Flow | Done | `HybridJudge` |
| Server Tests | Done | `server/test_main.py`, `server/test_judge_service.py` |
| Local Pipeline Tests | Done | `HybridJudgeTest` |
| Reference Docs | Done | `reference/README.md` |
| Azure Prep Docs | Done | `server/README.md` |

## Flow

```text
Local Verdict Store
  ↓ miss
Rule Engine
  ↓ uncertain
On-device Model
  ↓ low confidence / unknown
Local RAG
  ↓ still low confidence
Remote Fallback
```

## Model Status

```text
model/artifacts/url_risk_centroid_model.json
  -> Kotlin nearest-centroid runtime
  -> UrlFeatureExtractor vector
  -> LOW / MEDIUM / HIGH / UNKNOWN Verdict
```

현재 모델은 TFLite/ONNX 전환 전 단계의 MVP 연결이다. 학습 결과는 Android 판정 흐름에서 사용되지만, 추후 assets JSON, TFLite, ONNX 중 하나로 교체할 수 있다.

## Remaining

| Work | Description |
| :-- | :-- |
| Android UI/Shield | `Verdict`를 실제 경고/차단 UI에 매핑 |
| Azure deployment | `server/` FastAPI 앱 배포 |
| Dataset expansion | 검증된 URL 샘플 확장과 라벨 품질 개선 |
| Model runtime upgrade | Kotlin 내장 centroid 값을 assets JSON, TFLite, ONNX로 전환 |
| Device testing | 실제 Android 기기에서 end-to-end 확인 |

## Verification

```bash
./gradlew test
cd server
../.venv/bin/python -m unittest discover .
```

최근 확인:

```text
Gradle Kotlin tests: BUILD SUCCESSFUL
Python server tests: 7 passed
git diff --check: clean
```
