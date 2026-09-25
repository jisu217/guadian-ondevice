# URL Risk Centroid Model Card

This card defines what the local URL-risk model is, what it may be used for,
and which claims about it are not supported.

## Table of Contents

1. [Overview](#1-overview)
2. [Intended Use](#2-intended-use)
3. [Data](#3-data)
4. [Metrics](#4-metrics)
5. [Runtime Contract](#5-runtime-contract)
6. [Forbidden Claims](#6-forbidden-claims)
7. [Update Rules](#7-update-rules)

## 1. Overview

The model is a compact nearest-centroid URL-feature classifier that runs as
local Android/Kotlin inference (`OnDeviceModelJudge`) inside the hybrid
pipeline. Its parameters are a JSON artifact,
`model/artifacts/url_risk_centroid_model.json`, trained by
`model/training/train_model.py` on `model/data/sample_urls.csv`; the feature
contract is defined in [Model Specification](../docs/model-spec.md).

It is not a fine-tuned LLM and not a TFLite/ONNX artifact.
This model is not a production security detection service.

## 2. Intended Use

The model provides an on-device advisory signal for URL risk judgment. For
clear URL-level patterns, such as APK download lures and impersonation-like
login or authentication URLs, it short-circuits the pipeline with a `CERTAIN`
local verdict. When the URL-only signal is uncertain, it returns `UNKNOWN` /
`UNCERTAIN`, and the runtime falls through to local policy retrieval,
page-content inspection, or remote fallback. The final user-facing verdict is
owned by the full hybrid pipeline, not by this model alone.

## 3. Data

The dataset has 668 sanitized rows: 301 `synthetic`, 275 `reference-derived`,
and 92 `public-benign`. Each row records how its label was reviewed:

| `reviewed_by` | Rows | Meaning |
| :-- | --: | :-- |
| `manual-review` | 337 | manually assigned or checked during prototype construction |
| `llm-consensus-claude-gemini-openai` | 217 | label from sibling multi-model consensus data |
| `rule-policy` | 83 | deterministic project policy label |
| `human-review` | 31 | human-reviewed row from the sibling gold data |

LLM-consensus labels are treated as prototype evidence, not audited production
truth. Lineage is documented in [Provenance](../../docs/provenance.md).

## 4. Metrics

Metrics come from a deterministic stratified holdout of 536 training and 132
test rows ([Trained Model Report](trained-model-report.md)):

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy on same split | 68.94% |
| 5-fold CV accuracy | 81.19% |
| 5-fold CV macro F1 | 82.70% |

| Label | Precision | Recall | F1 |
| :-- | --: | --: | --: |
| `LOW` | 85.37% | 74.47% | 79.55% |
| `MEDIUM` | 77.50% | 70.45% | 73.81% |
| `HIGH` | 90.91% | 95.24% | 93.02% |
| `UNKNOWN` | 65.52% | 95.00% | 77.55% |

## 5. Runtime Contract

Python training and the Kotlin runtime share feature names, feature order, and
missing-value behavior, and `model/data/feature_parity_cases.csv` is tested by
both implementations to catch drift. Kotlin reads the generated JSON artifact
directly from resources, so there is a single source of model parameters.
Per-label distance thresholds decide whether the model emits a confident local
signal or lets the hybrid pipeline continue.

## 6. Forbidden Claims

The following claims are not supported:

- The model is production-ready.
- The model proves a URL is safe.
- The dataset represents real production traffic.
- LLM-consensus labels are equivalent to verified incident labels.
- The Android prototype has been validated on a physical device.
- The local model replaces page rendering, redirect analysis, domain reputation,
  accessibility-triggered judgment, or network security tooling.

## 7. Update Rules

When changing data or features:

1. Keep `source`, `reason`, `category`, `reviewed_by`, and `created_at` filled.
2. Add Python/Kotlin feature parity rows for new URL patterns.
3. Regenerate the trained model, baseline report, trained report, artifact, and
   split files.
4. Run model documentation, Python, server, and Kotlin tests before committing.
