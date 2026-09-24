# GUADIAN On-device Judge

Local-first Android URL-risk judgment prototype evolved from the SKT FLY AI
bootcamp GUADIAN project.

- Status: active research prototype
- Runtime: Android app module (`:app`) + Python model workspace + FastAPI fallback
- Trigger paths: manual URL entry and browser-click Shield accessibility service
- Model: URL-feature nearest-centroid classifier, not a fine-tuned LLM
- Verification: unit tests, Robolectric tests, CI, and emulator manual checks

The project explores how much URL-risk judgment can happen on-device before
falling back to a remote judge. It combines deterministic URL rules, a compact
local model, local policy retrieval, WebView-based page inspection, and a
server fallback for unresolved cases.

## Scope

The current repository contains the Android runtime, the local model workspace,
and the remote fallback server. The Android app exposes manual URL judgment
through `JudgeActivity` and browser-click protection through
`ShieldAccessibilityService`. Both paths use the same layered `HybridJudge`
pipeline: URL rules, local model inference, local policy retrieval,
page-content inspection, and remote fallback.

Model training, evaluation, provenance tracking, the model card, and CI
documentation checks are kept in the same repository so runtime behavior and model
claims can be reviewed together.

Current limits are summarized here; the full limitations and follow-up work are
tracked in [Technical Report](docs/technical-report.md#limitations) and
[Next Steps](docs/technical-report.md#next-steps).

- physical-device validation is not done yet
- Shield is limited to supported browser-click surfaces
- model dataset is small and partly synthetic

## Judgment Flow

```text
URL
  -> InMemoryRiskStore
  -> RuleBasedJudge
  -> LocalReasoningJudge
       -> OnDeviceModelJudge
       -> LocalKnowledgeJudge
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
`MEDIUM` and `UNKNOWN` remain context-sensitive. See the
[Model Workspace](model/README.md) and
[Technical Report](docs/technical-report.md#evaluation) for details.

## Repository Layout

```text
app/        Android runtime, Shield, UI, local judgment pipeline
model/      dataset, feature extraction, training, artifact, reports
server/     FastAPI remote fallback
docs/       architecture, technical report, provenance
reference/  preserved source/reference material
scripts/    repository checks
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
bash scripts/check-model-requirements.sh
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

| Document | Purpose |
| :-- | :-- |
| [Architecture](docs/architecture.md) | runtime structure and component boundaries |
| [Technical Report](docs/technical-report.md) | project motivation, evaluation, limits, and next steps |
| [Provenance](docs/provenance.md) | source lineage, reference boundaries, and dataset origin |
| [Model Workspace](model/README.md) | model files, commands, and workspace overview |
| [Model Card](model/evaluation/model-card.md) | intended use, metrics, and forbidden claims |
| [Remote Fallback Server](server/README.md) | FastAPI fallback scope and local operation |
