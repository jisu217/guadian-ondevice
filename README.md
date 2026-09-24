# GUADIAN On-device Judge

![Status](https://img.shields.io/badge/status-active%20prototype-blue)
![Runtime](https://img.shields.io/badge/runtime-Kotlin%2FJVM-orange)
![Model](https://img.shields.io/badge/model-nearest--centroid-green)
![Android](https://img.shields.io/badge/android-integration%20pending-lightgrey)

Local-first URL risk judgment pipeline for ad landing URLs.

> Status: This project is an active research prototype.  
> The current implementation runs as a Kotlin/JVM local judgment engine and has
> not yet been validated as a complete Android application.

This repository refactors the GUADIAN risk-judgment concept into a lightweight
local judgment engine. The current implementation is a Kotlin/JVM prototype
intended for Android integration: it can classify URLs locally with rules,
a compact feature-based model, local policy retrieval, and remote fallback for
uncertain cases.

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

Not yet implemented:

- Android UI / Shield screen
- device or emulator end-to-end validation
- AccessibilityService, WebView, or browser URL capture integration
- production deployment of the fallback server
- TFLite/ONNX runtime conversion

## Judgment Flow

```text
URL
  -> LocalRiskStore
  -> RuleBasedJudge
  -> OnDeviceModelJudge
  -> LocalRagJudge
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
| Holdout accuracy | 72.73% |
| Holdout macro F1 | 74.15% |
| Baseline accuracy | 68.94% |
| Cross-validation accuracy | 73.16% |
| Cross-validation macro F1 | 74.95% |

The evaluation intentionally includes realistic ad landing URLs. Accuracy drops
compared with synthetic-only data, but the failure modes are now visible:
`HIGH` is relatively strong, while `MEDIUM` and `UNKNOWN` remain difficult with
URL-only signals.

## Repository Layout

```text
android/app/src/main/kotlin/com/guardian/ondevice/
  judge/      pipeline orchestration and remote fallback client
  rules/      deterministic URL risk rules
  model/      URL features and local model runtime
  rag/        local policy-based fallback
  store/      local verdict cache

model/
  data/       sanitized samples and official-domain allowlist
  training/   feature extraction, baseline, training, evaluation scripts
  artifacts/  trained model parameters
  evaluation/ generated model reports

server/       FastAPI remote fallback server
docs/         architecture, project report, source/reference notes
reference/    copied reference materials and external source records
```

## Run Tests

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
- [Source And Reference Notes](docs/source-and-reference.md): what was copied,
  derived, or only used as reference
- [Data Policy](docs/data-policy.md): allowed claims, forbidden claims, and
  dataset governance rules
- [Model Card](model/evaluation/model-card.md): artifact role, metrics,
  intended use, and limitations
- [Model Workspace](model/README.md): dataset, training, and evaluation workflow
- [Remote Fallback Server](server/README.md): FastAPI fallback setup
