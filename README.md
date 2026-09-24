# GUADIAN On-device Judge

Local-first Android URL-risk judgment prototype evolved from the SKT FLY AI
bootcamp GUADIAN project.

| Field | Current state |
| :-- | :-- |
| Status | Active research prototype |
| Runtime | Android app module (`:app`) + Python model workspace + FastAPI fallback |
| Trigger paths | Manual URL entry and browser-click Shield accessibility service |
| Model | URL-feature nearest-centroid classifier, not a fine-tuned LLM |
| Verification | Unit tests, Robolectric tests, CI, and emulator manual checks |

The project explores how much URL-risk judgment can happen on-device before
falling back to a remote judge. It combines deterministic URL rules, a compact
local model, local policy retrieval, WebView-based page inspection, and a
server fallback for unresolved cases.

## Scope

Implemented:

- Android app module with `JudgeActivity`
- `ShieldAccessibilityService` for supported browser link-click detection
- layered `HybridJudge` pipeline
- URL rules, local model, local RAG, page-content inspection, and remote fallback
- Python model training and evaluation workflow
- dataset provenance, model card, and CI governance checks

Current limits:

- physical-device validation is not done yet
- Shield is limited to configured browser packages
- arbitrary in-app WebViews/native ad surfaces are not covered
- UI is functional, not product-polished
- model dataset is small and partly synthetic
- remote fallback server is not production-deployed here

## Judgment Flow

```text
URL
  -> LocalRiskStore
  -> RuleBasedJudge
  -> OnDeviceModelJudge
  -> LocalRagJudge
  -> PageContentJudge
  -> RemoteJudge
  -> Verdict
```

Each `Verdict` contains `risk`, `reason`, `advice`, `source`, `confidence`,
`type`, and optional `site` context.

## Model Snapshot

Evaluation uses 668 sanitized samples:

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy | 68.94% |
| Cross-validation accuracy | 81.19% |
| Cross-validation macro F1 | 82.70% |

`HIGH` is strongest on clear APK and impersonation-like URL patterns.
`MEDIUM` and `UNKNOWN` remain context-sensitive and often require page content,
redirect-chain, or policy evidence beyond URL text.

## Repository Layout

```text
app/        Android runtime, Shield, UI, local judgment pipeline
model/      dataset, feature extraction, training, artifact, reports
server/     FastAPI remote fallback
docs/       architecture, technical report, provenance
reference/  preserved source/reference material
scripts/    repository governance checks
```

## Run

```bash
./gradlew installDebug
adb shell am start -n com.guardian.ondevice/.JudgeActivity
```

To test Shield manually, install the app, enable its accessibility service in
Android settings, then click an off-site link in a supported browser.

## Check

```bash
bash scripts/check-model-governance.sh
python3 -m unittest discover model/training
.venv/bin/python -m unittest discover server
./gradlew test
```

Instrumented Android checks require a running emulator/device:

```bash
./gradlew connectedDebugAndroidTest
```

## Train

```bash
python3 model/training/train_model.py
```

Regenerates the model artifact, train/test split, and trained-model report under
`model/artifacts/` and `model/evaluation/`.

## Documents

- [Architecture](docs/architecture.md)
- [Technical Report](docs/technical-report.md)
- [Provenance](docs/provenance.md)
- [Model Workspace](model/README.md)
- [Model Card](model/evaluation/model-card.md)
- [Remote Fallback Server](server/README.md)
