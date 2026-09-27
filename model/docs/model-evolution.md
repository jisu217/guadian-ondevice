# Model Evolution

This document records how the local URL-risk model evolved and why the current
model shape was chosen. Latest numbers are generated in
[Trained Model Report](../evaluation/trained-model-report.md).

## Table of Contents

1. [Version History](#1-version-history)
2. [Dataset Milestones](#2-dataset-milestones)
3. [Current Metrics](#3-current-metrics)
4. [Confidence Calibration](#4-confidence-calibration)
5. [Key Findings](#5-key-findings)
6. [Current Model Definition](#6-current-model-definition)
7. [Completed Runtime Improvements](#7-completed-runtime-improvements)
8. [Next Experiments](#8-next-experiments)
9. [Data Collection Priorities](#9-data-collection-priorities)

## 1. Version History

**v1, rule-scoring baseline.** `train_baseline.py` sums hand-written feature
scores (APK, login, brand, event, free, shortener, and suspicious-TLD signals)
into a risk label. It is easy to explain but too coarse for mixed ad URLs,
reaching 68.41% accuracy on the full 668-row dataset and 68.94% on the final
holdout split (62.57% and 64.39% after the v8-prep feature change below). It
is kept as the comparison baseline.

**Exception — v2, synthetic-only centroid.** A nearest-centroid classifier trained on
balanced synthetic and policy-derived samples reached 97.50% accuracy, but the
dataset was too controlled to represent real ad landing URLs. It is treated as
an over-optimistic checkpoint, not a final claim.

**v3, realistic data merge.** Adding 248 real ad landing rows from
`model/data/raw/gold_251.jsonl`, then targeted banking, short-link, APK, and
Korean-domain edge cases, produced the 668-row dataset. Holdout accuracy fell to
72.73% (macro F1 74.15%), exposing that `MEDIUM` and `UNKNOWN` often require
signals beyond URL text. This dataset was adopted as the evaluation basis.

**v4–v5, confidence thresholds.** v4 used one distance threshold shared by all
labels, so a boundary case in one label suppressed confidence for unrelated
labels; in practice, a `LOW`/`MEDIUM` boundary issue collapsed confidence for
well-separated `HIGH` cases. v5 replaced it with a separate threshold per
predicted label, calibrated by 5-fold cross-validation on the training split.
`HIGH` confidence recovered, but `MEDIUM` still needed separation from long
benign ad/tracking URLs.

**v6–v7, feature changes.** v6 applies `log1p` to URL, host, path, and
query lengths before min-max scaling, so very long tracking URLs no longer
dominate the scale. v7 adds query count, UTM/tracking parameter count, nested
URL, encoded URL, ad-click host, and base64-like token features, which let the
error buckets separate benign tracking over-risk from context-dependent
`MEDIUM` misses.

Both were committed and retrained together in `a95b51b`. Their separate effect
was measured by an ablation run (2026-09-25) on the same dataset, split, and
training code, with v7 features dropped or `log1p` disabled:

| Variant | Source | Holdout acc. | Holdout macro F1 | `MEDIUM` recall | CV acc. | CV macro F1 |
| :-- | :-- | --: | --: | --: | --: | --: |
| v3–v5 (no `log1p`, no v7 features) | report at `a95b51b^`; ablation reproduces it | 72.73% | 74.15% | 50.00% | 73.16% | 74.95% |
| v6 only (`log1p`, no v7 features) | ablation | 79.55% | 80.98% | 70.45% | 79.69% | 81.18% |
| v7 only (v7 features, no `log1p`) | ablation | 79.55% | 80.72% | 65.91% | 80.63% | 82.04% |
| v6 + v7 | report at `a95b51b`; ablation reproduces it | 79.55% | 80.98% | 70.45% | 81.19% | 82.70% |

v6 accounts for the whole holdout gain. Adding v7 on top leaves holdout
accuracy unchanged and raises 5-fold CV from 79.69% to 81.19% accuracy and from
81.18% to 82.70% macro F1.

**v8-prep, `.test` leak removed (current).** The `v8` label is reserved for
the model trained after the remaining open issues are addressed; this is the
preparation step. v7 listed `test` among the suspicious
TLDs (and `short.test` among shortener hosts). The synthetic rows use the
reserved `.test` TLD, and 66 of the 107 `HIGH` rows are `.test` hosts, so the
feature partly encoded "this row is synthetic" rather than "this URL is risky".
v8-prep drops both entries, retrains on the same data and split, and adds a
per-`source` holdout breakdown to the report. The v7 synthetic/non-synthetic
columns were measured on 2026-09-25 by evaluating the committed v7 artifact
and v7 feature code on the same holdout split, grouped by `source`; the other
v7 columns are from the v7 report:

| Variant | Holdout acc. | Holdout macro F1 | `HIGH` F1 | Synthetic acc. (60) | Non-synthetic acc. (72) | CV acc. | CV macro F1 |
| :-- | --: | --: | --: | --: | --: | --: | --: |
| v7 | 79.55% | 80.98% | 93.02% | 93.33% | 68.06% | 81.19% | 82.70% |
| v8-prep | 75.00% | 74.63% | 75.68% | 85.00% | 66.67% | 80.44% | 81.44% |

Most of the drop is on synthetic rows, where the leaked signal lived. The v8-prep
`HIGH` misses are mostly `.apk` URLs on `.test` hosts, which `ApkRule` resolves
before the model runs in the app pipeline.

## 2. Dataset Milestones

The dataset grew in three stages. A 120-row seed set (30 per label) validated
the initial features and pipeline. A 400-row prototype set of synthetic,
policy-derived, and public-benign rows supported the baseline vs trained-model
comparison. The current 668-row realistic set adds the 248 real ad landing rows
and targeted edge cases, and is the evaluation basis today. It contains 301
`synthetic`, 275 `reference-derived` (248 of them from
`model/data/raw/gold_251.jsonl`), and 92 `public-benign` rows.

The realistic stage intentionally lowered headline accuracy: the model is now
evaluated against cases closer to actual ad traffic, not only controlled
synthetic patterns.

## 3. Current Metrics

On the holdout split (536 train / 132 test):

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 75.00% |
| Holdout macro F1 | 74.63% |
| Baseline accuracy (same split) | 64.39% |
| 5-fold CV accuracy | 80.44% |
| 5-fold CV macro F1 | 81.44% |

| Label | Precision | Recall | F1 | Interpretation |
| :-- | --: | --: | --: | :-- |
| `LOW` | 85.37% | 74.47% | 79.55% | acceptable; long ad/tracking URLs still cause misses |
| `MEDIUM` | 75.61% | 70.45% | 72.94% | often needs page-level evidence |
| `HIGH` | 87.50% | 66.67% | 75.68% | APK misses are left to `ApkRule` in the pipeline |
| `UNKNOWN` | 55.88% | 95.00% | 70.37% | intentionally conservative, but overused |

The 33 holdout errors fall into these buckets:

| Bucket | Count |
| :-- | --: |
| `LOW` benign URL lacks enough local context | 7 |
| `MEDIUM` needs page or interaction context | 7 |
| `MEDIUM` under-risked by URL-only signal | 6 |
| `HIGH` malicious signal too weak in URL text | 6 |
| `LOW` tracking/ad URL over-risked | 5 |
| `HIGH` impersonation signal below threshold | 1 |
| `UNKNOWN` over-classified by sparse URL signal | 1 |

## 4. Confidence Calibration

A nearest-centroid prediction becomes a `CERTAIN` local verdict only if its
distance is within the calibrated threshold for the predicted label: 0.4383 for
`LOW`, 0.8128 for `MEDIUM`, 1.1989 for `HIGH`, and 0.0898 for `UNKNOWN`.
Otherwise the result is `UNKNOWN` / `UNCERTAIN` and falls through to local
knowledge matching or page-content inspection.

## 5. Key Findings

- **Synthetic-only results overstated quality.** Accuracy fell from 97.50% to
  79.55% once realistic rows, edge cases, and current feature scaling were in
  place, so realistic rows stay in the main evaluation set.
- **Synthetic artifacts can leak into features.** The `.test` suspicious-TLD
  entry inflated `HIGH` F1 to 93.02%; without it `HIGH` F1 is 75.68%. Metrics
  are now also reported per data source.
- **Rules remain useful as a baseline.** At 62.57% (full dataset) and 64.39%
  (holdout), `train_baseline.py` is kept for comparison and sanity checks.
- **`HIGH` is only partly learnable from URL features.** Holdout `HIGH`
  precision is 87.50% but recall 66.67%; the model still short-circuits
  confident high-risk patterns, and deterministic rules cover APK URLs.
- **`MEDIUM` improved but remains context-sensitive.** Holdout `MEDIUM` recall
  rose from 50.00% to 70.45%, but many cases still need page content or
  redirect context, so uncertain `MEDIUM` cases are treated as fallback
  candidates.
- **One global threshold was too brittle.** A boundary case in one label
  affected confidence for others, which led to per-label thresholds.

## 6. Current Model Definition

At runtime, the model turns a URL into a verdict as follows:

```text
URL
  -> deterministic 22-feature vector        UrlFeatureExtractor
  -> log(1 + x) on 4 length features
  -> min-max scaling (artifact min/max)
  -> nearest label centroid
  -> per-label distance threshold
       distance <= threshold, label != UNKNOWN  -> CERTAIN verdict
       otherwise                                -> UNKNOWN / UNCERTAIN
```

The same logic is split across Python training and Kotlin inference:

| File | Role |
| :-- | :-- |
| `model/training/url_features.py` | Python feature extraction |
| `model/training/train_baseline.py` | rule-scoring baseline |
| `model/training/train_model.py` | training, cross-validation, threshold calibration, report generation |
| `model/runtime/url_risk_centroid_model.json` | generated model parameters |
| `core/judge/src/main/kotlin/com/guadian/ondevice/classifier/UrlFeatureExtractor.kt` | Kotlin runtime feature extraction |
| `core/judge/src/main/kotlin/com/guadian/ondevice/classifier/OnDeviceModelJudge.kt` | Kotlin runtime inference |

## 7. Completed Runtime Improvements

On the model side, `NearestCentroidUrlModel.default` now reads
`url_risk_centroid_model.json` as a classpath resource, removing manual
duplication between the Python artifact and the Kotlin runtime. To prevent
training/runtime feature drift, `feature_parity_cases.csv` is checked by both
Python and Kotlin tests. The log-compressed length features and the
redirect/ad-network features are applied identically in Python training and
Kotlin inference, and the parity fixture covers them.

Two runtime changes sit outside the URL-feature model. `PageContentJudge` runs
after the model and knowledge layers to inspect rendered page content
on-device, covering form, brand-wording, and rendered-content evidence that URL
features miss. `ShieldAccessibilityService` adds an accessibility trigger for
browser link clicks, where the pipeline previously depended on manual URL
entry; it is tracked here only because it changes where the model is invoked
(see [Architecture](../../docs/architecture.md)).

## 8. Next Experiments

The next model experiments are real redirect-chain resolution, since current
features detect nested URLs but do not follow the actual HTTP chain, and a
revisit of the `MEDIUM` and `UNKNOWN` labeling policy to separate true
uncertainty from suspicious-but-not-severe flows. Project-level runtime
follow-up is tracked in
[Technical Report](../../docs/technical-report.md#10-next-steps).

## 9. Data Collection Priorities

Schema and safety rules are defined in
[Model Specification](model-spec.md#1-dataset). Collection priorities, in
order:

1. More human-reviewed real ad-landing URLs.
2. More `MEDIUM` examples with a clear page-level rationale.
3. More benign tracking-heavy URLs, to reduce false positives on normal ad
   flows.
4. More redirect/ad-network cases with explicit categories.
5. Keep `HIGH` examples diverse; realistic ad traffic surfaces few obvious
   high-risk samples.
