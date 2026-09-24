# GUADIAN On-device Judge

Local-first URL risk judgment pipeline evolved from the SKT FLY AI bootcamp
GUADIAN project.

| Field | Current state |
| :-- | :-- |
| Status | Active research prototype |
| Runtime | Real Android app (Gradle/AGP), verified on emulator |
| Model | URL-feature nearest-centroid classifier |
| Android scope | Minimal URL-input -> Verdict screen; Shield/browser interception not built |

This repository takes the original GUADIAN ad-risk judgment concept developed
during the SKT FLY AI bootcamp and reorganizes it as an on-device-oriented
local judgment engine. Instead of relying only on a remote server judgment flow,
the Android app can classify URLs locally with deterministic rules, a compact
feature-based model, local policy retrieval, on-device page-content inspection,
and remote fallback for uncertain cases.

## What This Project Does

Given an ad landing URL, the pipeline returns an explainable `Verdict`:

- `LOW`: allow navigation
- `MEDIUM`: require user confirmation
- `HIGH`: block or strongly warn
- `UNKNOWN`: not enough local evidence; use fallback

Each verdict includes `reason`, `advice`, `source`, `confidence`, and `type`
so the decision can be shown to users or inspected during evaluation.

## Current Scope

Implemented:

- Kotlin local judgment pipeline
- deterministic URL rules
- feature-based nearest-centroid model
- local RAG-style policy fallback
- FastAPI remote fallback server
- Python model training and evaluation workflow
- sanitized dataset and reference-source documentation
- Kotlin and Python unit tests
- real Android app build (AGP/Gradle), installed and run on emulator
- minimal UI (`JudgeActivity`): URL input -> Verdict display
- on-device page-content inspection (`PageContentJudge`): renders the
  destination URL in a hidden WebView and flags an unverified login form or a
  page/host brand mismatch, entirely on-device
- all 5 judgment layers (rule/model/RAG/page-content/remote) verified
  end-to-end on emulator, including safe failure handling when the remote
  fallback is unreachable
- Shield (`ShieldAccessibilityService`): detects a link click that leaves the
  current site in Chrome/Samsung Internet, judges the destination with the
  same `HybridJudge` pipeline, and shows the verdict behind a full-screen
  on-device overlay -- manually verified end-to-end on emulator against real
  Chrome (see [Architecture](docs/architecture.md) for the verification notes
  and known limitations)

Not yet implemented:

- physical device testing (emulator only so far)
- risk-based UI styling, app icon, result history
- production deployment of the fallback server
- TFLite/ONNX runtime conversion

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

The design is intentionally conservative. Clear local evidence is handled on
device; uncertain cases are escalated to local policy retrieval or remote
fallback instead of forcing a confident local decision.

## Model Summary

The model is not a fine-tuned LLM. It is a compact URL-feature classifier built
for local inference:

- deterministic URL feature extraction
- nearest-centroid classifier
- baseline comparison
- 5-fold stratified cross-validation
- label-level precision, recall, F1, and error analysis
- calibrated per-label confidence thresholds

Current evaluation with 668 sanitized samples:

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy | 68.94% |
| Cross-validation accuracy | 81.19% |
| Cross-validation macro F1 | 82.70% |

The evaluation intentionally includes realistic ad landing URLs. Accuracy drops
compared with synthetic-only data, but the failure modes are now visible:
`HIGH` is relatively strong, while `MEDIUM` and `UNKNOWN` remain difficult with
URL-only signals.

## Repository Layout

```text
app/src/main/
  AndroidManifest.xml
  res/                                   layout, strings, theme
  kotlin/com/guardian/ondevice/
    JudgeActivity.kt   URL input -> Verdict display screen
    judge/      pipeline orchestration and remote fallback client
    rules/      deterministic URL risk rules
    model/      URL features and local model runtime
    rag/        local policy-based fallback
    store/      local verdict cache
    webview/    on-device page-content inspection (hidden WebView + DOM check)

model/
  data/       sanitized samples and official-domain allowlist
  training/   feature extraction, baseline, training, evaluation scripts
  artifacts/  trained model parameters
  evaluation/ generated model reports

server/       FastAPI remote fallback server
docs/         architecture, project report, source/reference notes
reference/    copied reference materials and external source records
```

## Run The App

Requires Android Studio / SDK with an emulator (or device) already set up:

```bash
./gradlew installDebug
adb shell am start -n com.guardian.ondevice/.JudgeActivity
```

## Run Tests

Instrumented UI tests (requires a running emulator/device; automates the same
5-layer check — rule/model/RAG/page-content/remote — done by hand during
development):

```bash
./gradlew connectedDebugAndroidTest
```

Kotlin pipeline tests:

```bash
./gradlew test
```

Python model tests:

```bash
python3 -m unittest discover model/training
```

Server tests:

```bash
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
cd server
../.venv/bin/python -m unittest discover .
```

Repository risk gates:

```bash
bash scripts/check-model-governance.sh
```

## Train The Model

```bash
python3 model/training/train_model.py
```

This regenerates:

- `model/artifacts/url_risk_centroid_model.json`
- `model/evaluation/trained-model-report.md`

## Key Documents

- [Project Report](docs/project-report.md): complete technical narrative,
  evaluation results, limitations, and next steps
- [Architecture](docs/architecture.md): runtime pipeline and component roles
- [Provenance](docs/provenance.md): what was copied,
  derived, or only used as reference
- [Model Card](model/evaluation/model-card.md): artifact role, metrics, intended
  use, limitations, and dataset governance rules
- [Model Workspace](model/README.md): dataset, training, and evaluation workflow
- [Remote Fallback Server](server/README.md): FastAPI fallback setup
