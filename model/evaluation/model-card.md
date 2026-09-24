# URL Risk Centroid Model Card

## Overview

- Artifact: `model/artifacts/url_risk_centroid_model.json`
- Type: nearest-centroid URL-feature classifier
- Runtime: Android/Kotlin local inference inside the hybrid pipeline
- Training code: `model/training/train_model.py`
- Feature contract: `model/docs/model-spec.md`
- Dataset: `model/data/sample_urls.csv`

This model is a compact local URL-risk classifier. It is not a fine-tuned LLM,
not a TFLite/ONNX artifact, and not a production security detection service.

## Intended Use

The model provides an on-device advisory signal for URL risk judgment. It is
intended to short-circuit clear URL-level patterns such as APK download lures
and impersonation-like login or authentication URLs. When the URL-only signal is
uncertain, the runtime falls through to local policy retrieval, page-content
inspection, or remote fallback.

The final user-facing verdict is owned by the full hybrid pipeline, not by this
model alone.

## Data

Current dataset size: 668 sanitized rows.

| Source | Rows |
| :-- | --: |
| `synthetic` | 301 |
| `reference-derived` | 275 |
| `public-benign` | 92 |

Review provenance:

| `reviewed_by` value | Meaning |
| :-- | :-- |
| `rule-policy` | deterministic project policy label |
| `manual-review` | manually assigned or checked during prototype construction |
| `human-review` | human-reviewed row from the sibling gold data |
| `llm-consensus-claude-gemini-openai` | label from sibling multi-model consensus data |

LLM-consensus labels are treated as prototype evidence, not audited production
truth.

## Metrics

Latest deterministic holdout evaluation:

| Metric | Value |
| :-- | --: |
| Train samples | 536 |
| Test samples | 132 |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy on same split | 68.94% |
| 5-fold CV accuracy | 81.19% |
| 5-fold CV macro F1 | 82.70% |

Label-level holdout metrics:

| Label | Precision | Recall | F1 |
| :-- | --: | --: | --: |
| `LOW` | 85.37% | 74.47% | 79.55% |
| `MEDIUM` | 77.50% | 70.45% | 73.81% |
| `HIGH` | 90.91% | 95.24% | 93.02% |
| `UNKNOWN` | 65.52% | 95.00% | 77.55% |

## Runtime Contract

Python training and Kotlin runtime share feature names, feature ordering, and
missing-value behavior. `model/data/feature_parity_cases.csv` is tested by both
implementations, and Kotlin reads the generated JSON artifact directly from
resources. Per-label distance thresholds decide whether the model can emit a
confident local signal or should let the hybrid pipeline continue.

## Forbidden Claims

- The model is production-ready.
- The model proves a URL is safe.
- The dataset represents real production traffic.
- LLM-consensus labels are equivalent to verified incident labels.
- The Android prototype has been validated on a physical device.
- The local model replaces page rendering, redirect analysis, domain reputation,
  accessibility-triggered judgment, or network security tooling.

## Update Rules

When changing data or features:

1. Keep `source`, `reason`, `category`, `reviewed_by`, and `created_at` filled.
2. Add Python/Kotlin feature parity rows for new URL patterns.
3. Regenerate the trained model, baseline report, trained report, artifact, and
   split files.
4. Run model documentation, Python, server, and Kotlin tests before committing.
