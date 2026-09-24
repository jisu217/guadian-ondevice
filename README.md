# GUADIAN On-device

[![CI](https://github.com/jisu217/guadian-ondevice/actions/workflows/ci.yml/badge.svg)](https://github.com/jisu217/guadian-ondevice/actions/workflows/ci.yml)
![Status](https://img.shields.io/badge/status-active%20development-brightgreen)

On-device URL-risk judgment model and Android protection pipeline evolved from the SKT FLY AI bootcamp GUADIAN project.

* **Status:** Active development
* **Runtime:** Android app module (`:app`) + Python model workspace + FastAPI fallback
* **Protection path:** Browser-link navigation through the Shield accessibility service
* **Model:** URL-feature nearest-centroid classifier, not a fine-tuned LLM
* **Verification:** Unit tests, Robolectric tests, CI, and emulator manual checks

The original GUADIAN project was designed to protect users during potentially risky advertising and link-navigation flows. Rather than requiring users to inspect URLs themselves, GUADIAN intervenes in the normal browsing flow: low-risk destinations can continue, uncertain cases can require confirmation, and clearly risky destinations can be blocked before entry.

GUADIAN On-device develops the judgment layer further by moving part of that decision process onto the Android device. Instead of depending on a commercial LLM for every unresolved case, the project combines deterministic URL rules, a compact non-LLM model, local policy knowledge, and page-content inspection before using a remote fallback.

The goal is to increase the amount of judgment that can be completed locally, reduce unnecessary remote-server calls and communication overhead, and preserve the original GUADIAN protection flow while making the judgment architecture less dependent on remote inference.

## Scope

The repository contains three primary components:

* **Android runtime** — Shield, UI, and the local-first hybrid judgment pipeline
* **Model workspace** — feature extraction, training, evaluation, model artifacts, and provenance
* **Remote fallback server** — FastAPI-based fallback for cases that remain unresolved locally

The primary runtime path begins when Shield observes a supported browser-link navigation. The destination then enters the shared `HybridJudge` pipeline, where local evidence is evaluated before the system falls back to the remote judge.

`JudgeActivity` provides a direct judgment entry point for development and verification, but it is not the primary user interaction model.

Model training, evaluation, provenance tracking, the model card, and CI documentation checks are kept in the same repository so that runtime behavior and model claims can be reviewed together.

Current limitations include:

* Physical-device validation has not been completed yet.
* Shield is limited to supported browser navigation surfaces.
* The model dataset is small and partly synthetic.
* Reported evaluation results should not be interpreted as production security guarantees.

See the [Technical Report](docs/technical-report.md#limitations) and [Next Steps](docs/technical-report.md#next-steps) for details.

## Judgment Flow

```text
Browser link navigation
  -> ShieldAccessibilityService
  -> InMemoryRiskStore
  -> RuleBasedJudge
  -> LocalReasoningJudge
       -> OnDeviceModelJudge
       -> LocalKnowledgeJudge
       -> PageContentJudge
  -> RemoteJudge
  -> Verdict
```

Each stage can resolve a request when sufficient evidence is available. Cases that remain unresolved fall through to the next stage, with `RemoteJudge` serving as the final fallback.

Each `Verdict` contains `risk`, `reason`, `advice`, `source`, `confidence`, `type`, and optional `site` context.

## Model Snapshot

The current model evaluation uses **668 sanitized samples**.

| Metric                    |  Value |
| ------------------------- | -----: |
| Holdout accuracy          | 79.55% |
| Holdout macro F1          | 80.98% |
| Baseline accuracy         | 68.94% |
| Cross-validation accuracy | 81.19% |
| Cross-validation macro F1 | 82.70% |

`HIGH` is strongest on clear APK and impersonation-like URL patterns. `MEDIUM` and `UNKNOWN` remain more context-sensitive.

See the [Model Workspace](model/README.md) and [Technical Report](docs/technical-report.md#evaluation) for the evaluation methodology, dataset limitations, provenance, and model details.

### Local Resolution

On the same 668-sample evaluation dataset used in the
[Technical Report evaluation](docs/technical-report.md#evaluation), **39.82%**
of cases resolve locally before reaching the remote fallback:

* 138 by deterministic rules
* 110 by the model or local knowledge
* 18 additional cases by page-content inspection

This is an **evaluation-set measurement**, not an estimate of production traffic or real-world remote-call reduction.

## Repository Layout

The repository is split by concern so that the Android runtime, model workspace, and fallback server can be inspected and tested independently.

```text
app/        Android runtime, Shield, UI, local judgment pipeline
model/      Dataset, feature extraction, training, artifacts, reports
server/     FastAPI remote fallback
docs/       Architecture, technical report, provenance
reference/  Preserved source/reference material
scripts/    Repository and model checks
```

## Requirements

Before running the project, ensure that the development environment provides:

* Android SDK and Android development tooling
* A compatible JDK for the Android build
* Python 3
* An Android emulator or physical device for instrumented checks

Refer to the project build configuration for the exact Android, Gradle, JDK, and dependency versions used by the repository.

## Run

Install the debug build:

```bash
./gradlew installDebug
```

To exercise the Shield path, enable the GUADIAN accessibility service in Android settings and navigate through a supported browser link.

For direct development and verification of the judgment pipeline, `JudgeActivity` can be launched with:

```bash
adb shell am start -n com.guardian.ondevice/.JudgeActivity
```

The Android app uses the configured remote fallback only after local judgment
remains unresolved. To run and inspect the fallback server locally, see
[server/README.md](server/README.md).

## Check

The following checks cover the model requirements, Python model/server tests, and Kotlin/Android unit tests used by CI:

```bash
bash scripts/check-model-requirements.sh
python3 -m unittest discover model/training
python3 -m pip install -r server/requirements.txt
python3 -m unittest discover server
./gradlew test
```

Instrumented Android checks require a running emulator or physical device:

```bash
./gradlew connectedDebugAndroidTest
```

## Train

Retrain the local model from the committed dataset:

```bash
python3 model/training/train_model.py
```

Training regenerates the relevant model outputs under `model/artifacts/` and `model/evaluation/`, including the model artifact, train/test split, and trained-model evaluation report.

## Model Documentation

Model development and evaluation evidence are maintained alongside the runtime implementation:

- [Model Workspace](model/README.md) — `model/README.md` 「Model development structure · training · evaluation · artifact overview」
- [Model Specification](model/docs/model-spec.md) — `model/docs/model-spec.md` 「URL features · classifier design · runtime integration specification」
- [Model Card](model/evaluation/model-card.md) — `model/evaluation/model-card.md` 「Model performance · intended use · limitations · forbidden claims」
- [Baseline Report](model/evaluation/baseline-report.md) — `model/evaluation/baseline-report.md` 「Baseline performance on the same split」
- [Trained Model Report](model/evaluation/trained-model-report.md) — `model/evaluation/trained-model-report.md` 「Trained model evaluation · baseline comparison」
- [Provenance](docs/provenance.md) — `docs/provenance.md` 「Data sources · labeling · synthetic data · artifact traceability」
- [Technical Report](docs/technical-report.md) — `docs/technical-report.md` 「Architecture · evaluation · limitations · next steps · implementation status by `GuADian · FULL SERVICE FLOW v2` frame」

The current model is under active development and evaluation. It is **not a production security detection service**.

## Project Status

GUADIAN On-device is under active development.

Current work focuses on improving the on-device URL-risk model, increasing local judgment coverage, validating Python/Kotlin feature parity, strengthening evaluation evidence, expanding device-level verification, and reducing dependence on remote fallback.
