# URL Risk Centroid Model Card

## Model Overview

| Field | Value |
| :-- | :-- |
| Model artifact | `model/artifacts/url_risk_centroid_model.json` |
| Model type | nearest-centroid URL-feature classifier |
| Runtime target | Kotlin/JVM local inference, intended for Android integration |
| Training code | `model/training/train_model.py` |
| Feature contract | `model/docs/feature-spec.md` |
| Dataset | `model/data/sample_urls.csv` |

This model is a compact local URL-risk classifier. It is not a fine-tuned LLM,
not a TFLite/ONNX artifact, and not a production security detection service.

## Intended Use

- Provide an on-device advisory signal for URL risk judgment.
- Short-circuit clear URL-level patterns such as APK download lures and
  impersonation-like login/authentication URLs.
- Fall through to local policy retrieval or remote fallback when the URL-only
  signal is uncertain.

The final user-facing verdict is owned by the hybrid judgment pipeline, not by
the model alone.

## Not Intended For

- Replacing browser, network, or rendered-page phishing analysis.
- Certifying that a URL is safe.
- Claiming production-level phishing detection performance.
- Evaluating private customer traffic or real incident outcomes.
- Making legal, financial, or compliance decisions.

## Data

Current dataset size: 668 sanitized rows.

| Source | Rows | Role |
| :-- | --: | :-- |
| `synthetic` | 301 | controlled feature and edge-case coverage |
| `reference-derived` | 275 | GUADIAN-related references and sibling gold data |
| `public-benign` | 92 | public official or normal URLs |

The dataset mixes synthetic, public-benign, LLM-consensus, and human-reviewed
rows. Row-level provenance is recorded in `source`, `reviewed_by`, and
`created_at`.

## Metrics

Latest deterministic holdout evaluation:

| Metric | Value |
| :-- | --: |
| Train samples | 536 |
| Test samples | 132 |
| Holdout accuracy | 72.73% |
| Holdout macro F1 | 74.15% |
| Baseline accuracy on same split | 68.94% |
| 5-fold CV accuracy | 73.16% |
| 5-fold CV macro F1 | 74.95% |

Label-level holdout metrics:

| Label | Precision | Recall | F1 | Note |
| :-- | --: | --: | --: | :-- |
| `LOW` | 81.40% | 74.47% | 77.78% | long ad/tracking URLs still cause misses |
| `MEDIUM` | 75.86% | 50.00% | 60.27% | weakest label; often needs page context |
| `HIGH` | 90.91% | 95.24% | 93.02% | strongest label for clear URL-level risk |
| `UNKNOWN` | 50.00% | 95.00% | 65.52% | conservative fallback-oriented label |

## Runtime Contract

- Python training and Kotlin runtime share the same feature names and ordering.
- `model/data/feature_parity_cases.csv` is tested by both Python and Kotlin.
- Kotlin reads the JSON artifact directly from resources.
- Per-label distance thresholds control whether the local model is confident
  enough to emit a direct signal.

## Known Limitations

- URL-only features cannot reliably distinguish many `MEDIUM` and `UNKNOWN`
  cases.
- Rendered page content, redirects, forms, domain age, and network reputation are
  not part of this artifact.
- The dataset is small for security modeling and does not represent production
  traffic distribution.
- LLM-consensus labels are useful for prototype evaluation, but are not equal to
  fully audited ground truth.

## Governance

Every model update should regenerate:

- `model/artifacts/url_risk_centroid_model.json`
- `model/evaluation/trained-model-report.md`
- `model/evaluation/baseline-report.md`
- `model/evaluation/train-split.csv`
- `model/evaluation/test-split.csv`

CI runs `scripts/check-model-governance.sh`, Python tests, server tests, and Kotlin
tests to guard against missing artifacts, feature drift, and unsupported claims.
