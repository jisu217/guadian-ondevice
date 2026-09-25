# Model Workspace

This directory contains the URL-risk model's dataset, training scripts,
generated artifact, and evaluation reports.

The model is a compact nearest-centroid URL-feature classifier used as one layer
(`OnDeviceModelJudge`) of the Android pipeline. It does not fetch page content
and is not a fine-tuned LLM.

## Table of Contents

1. [Layout](#1-layout)
2. [Dataset](#2-dataset)
3. [Results](#3-results)
4. [Commands](#4-commands)
5. [Runtime Integration](#5-runtime-integration)
6. [Related Documents](#6-related-documents)

## 1. Layout

| Path | Content |
| :-- | :-- |
| `data/sample_urls.csv` | sanitized training/evaluation samples |
| `data/official_domains.txt` | shared official-domain allowlist |
| `data/feature_parity_cases.csv` | Python/Kotlin feature parity fixture |
| `training/url_features.py` | Python feature extraction |
| `training/train_model.py` | nearest-centroid training, CV, threshold calibration, report |
| `training/train_baseline.py` | rule-scoring baseline |
| `training/build_official_domains.py` | allowlist builder |
| `training/test_*.py` | unit tests |
| `artifacts/url_risk_centroid_model.json` | generated model parameters |
| `evaluation/trained-model-report.md` | generated training report |
| `evaluation/baseline-report.md` | generated baseline report |
| `evaluation/local-resolution-report.md` | generated per-layer local resolution report (`LocalResolutionReportTest`) |
| `evaluation/train-split.csv`, `test-split.csv` | generated holdout split |
| `evaluation/model-card.md` | intended use, metrics, and limits |
| `docs/model-spec.md` | dataset schema and feature contract |
| `docs/model-evolution.md` | experiment history |

## 2. Dataset

`data/sample_urls.csv` holds 668 rows from three sources:

| Source | Rows | Content |
| :-- | --: | :-- |
| `synthetic` | 301 | controlled samples for feature and pipeline validation |
| `reference-derived` | 275 | 248 real ad landing rows from `gold_251.jsonl` + 27 GUADIAN-policy-derived rows |
| `public-benign` | 92 | public official/normal URLs |

Only sanitized fixtures are committed, and every row keeps its provenance in
`source`, `reviewed_by`, and `created_at`. The full schema and data-safety rules
are in [Model Specification](docs/model-spec.md#1-dataset); source lineage is in
[Provenance](../docs/provenance.md).

## 3. Results

The latest committed run uses a deterministic stratified holdout and 5-fold
cross-validation on the training split:

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Rule baseline accuracy (same split) | 68.94% |
| 5-fold CV accuracy | 81.19% |
| 5-fold CV macro F1 | 82.70% |

The model improves over the rule baseline, but URL-only inference is not
sufficient for every risk grade. `HIGH` is strongest, on clear APK and
impersonation-like patterns, while `MEDIUM` and `UNKNOWN` remain
context-sensitive and should be interpreted as part of the full hybrid
pipeline. Full metrics are in
[Trained Model Report](evaluation/trained-model-report.md).

## 4. Commands

Run from the repository root:

```bash
python3 -m unittest discover model/training
python3 model/training/train_model.py
python3 model/training/train_baseline.py
python3 model/training/build_official_domains.py --tranco /path/to/top-1m.csv
```

`train_model.py` regenerates the model artifact, the trained-model report, and
the train/test split files. `train_baseline.py` regenerates
`evaluation/baseline-report.md`, and `build_official_domains.py` rebuilds
`data/official_domains.txt` from a Tranco list.

## 5. Runtime Integration

`app/build.gradle.kts` adds `model/data` and `model/artifacts` to the app's
`main` resource directories, so Python training and the Kotlin runtime read the
same files. `OnDeviceModelJudge` loads `url_risk_centroid_model.json` as a
classpath resource, and both `url_features.py` and `UrlFeatureExtractor` read
`official_domains.txt`. Retraining therefore updates the runtime model directly,
with no export step.

## 6. Related Documents

[Model Specification](docs/model-spec.md) defines the dataset schema and feature
contract, and [Model Evolution](docs/model-evolution.md) records the experiment
history and modeling decisions. [Model Card](evaluation/model-card.md) states
intended use, metrics, and limits, and
[Trained Model Report](evaluation/trained-model-report.md) is the generated
latest training report.
