# Model Development History

This document records how the local URL-risk model evolved. It is separate from
the generated evaluation reports: the reports show the latest numbers, while
this file explains why the current model shape was chosen.

## 1. Model Evolution

| Version | Approach | Measured issue | Result |
| :-- | :-- | :-- | :-- |
| v1 rule-scoring baseline | Hand-written feature scores from `train_baseline.py`. APK, login, brand, event, free, shortener, and suspicious TLD signals are summed into a risk label. | Easy to explain, but too coarse for mixed ad URLs. On the full 648-sample dataset it reaches 68.67% accuracy. On the final holdout split it reaches 68.75%. | Kept as the comparison baseline. |
| v2 synthetic-only centroid | Nearest-centroid classifier trained on balanced synthetic/policy-derived samples. | Synthetic-only evaluation reached 97.50% accuracy, but the dataset was too controlled and did not represent real ad landing URLs. | Not used as the final claim; treated as an over-optimistic prototype checkpoint. |
| v3 realistic data merge | Added 248 usable real ad landing references from `reference/gold_251.jsonl`, producing 648 committed samples. | Accuracy dropped to 72.66% and macro F1 to 74.24%. This exposed the real weakness: `MEDIUM` and `UNKNOWN` require signals that URL text often cannot provide. | Adopted as the current evaluation basis because it is more realistic. |
| v4 global confidence threshold | Used one shared distance threshold for all labels. | A close boundary case in one label could suppress confidence for unrelated labels. In practice, a LOW/MEDIUM boundary issue collapsed confidence for `HIGH` cases that were otherwise well separated. | Replaced. |
| v5 per-label confidence thresholds | Calibrates a separate distance threshold for each predicted label using 5-fold cross-validation on the training split. | `HIGH` confidence recovered, but `MEDIUM` still rarely clears its threshold because many real medium-risk cases need page content, form detection, or rendered-page signals. | Current approach. |

## 2. Dataset Milestones

| Stage | Samples | Composition | Role |
| :-- | --: | :-- | :-- |
| Seed | 120 | 30 samples per label | initial feature and pipeline validation |
| Prototype | 400 | synthetic, policy-derived, and public-benign rows | baseline vs trained-model comparison |
| Realistic | 648 | prototype rows plus 248 real ad landing references | current evaluation basis |

The realistic stage intentionally lowered headline accuracy. The drop is useful:
it shows that the model is being evaluated against cases closer to actual ad
traffic instead of only controlled synthetic patterns.

The committed 648-row dataset is not a single-source corpus. It currently
contains 288 synthetic rows, 275 reference-derived rows, and 85 public-benign
rows. The largest imported local source is `reference/gold_251.jsonl`, which
contributed 248 usable real ad landing references.

## 3. Current Metrics

Latest trained-model report:

| Metric | Value |
| :-- | --: |
| Train samples | 520 |
| Test samples | 128 |
| Holdout accuracy | 72.66% |
| Holdout macro F1 | 74.24% |
| Baseline accuracy on same split | 68.75% |
| 5-fold CV accuracy | 72.52% |
| 5-fold CV macro F1 | 74.51% |

Holdout label metrics:

| Label | Precision | Recall | F1 | Interpretation |
| :-- | --: | --: | --: | :-- |
| `LOW` | 82.50% | 73.33% | 77.65% | acceptable, but long ad/tracking URLs still cause misses |
| `MEDIUM` | 75.86% | 51.16% | 61.11% | weakest label; often needs page-level evidence |
| `HIGH` | 90.48% | 95.00% | 92.68% | strongest label; clear APK and impersonation-like patterns work well |
| `UNKNOWN` | 50.00% | 95.00% | 65.52% | intentionally conservative, but overused |

## 4. Confidence Calibration

The current model does not return every nearest-centroid label as a confident
local verdict. It uses calibrated distance thresholds:

| Label | Threshold |
| :-- | --: |
| `LOW` | 0.3522 |
| `MEDIUM` | 0.7852 |
| `HIGH` | 1.0401 |
| `UNKNOWN` | 0.3066 |

This matters because the local model sits inside a hybrid safety pipeline.
If the nearest centroid is not confident enough, the result falls through to
local RAG or remote fallback instead of forcing a local decision.

## 5. Key Findings

| Finding | Evidence | Engineering decision |
| :-- | :-- | :-- |
| Synthetic-only results overstated quality. | Accuracy fell from 97.50% to 72.66% after realistic rows were added. | Keep realistic rows in the main evaluation set. |
| Rules remain useful as a baseline. | Full-dataset baseline accuracy is 68.67%; holdout baseline accuracy is 68.75%. | Keep `train_baseline.py` for comparison and sanity checks. |
| `HIGH` is learnable from URL features. | Holdout `HIGH` recall is 95.00%, F1 is 92.68%. | Let the model short-circuit confident high-risk URL patterns. |
| `MEDIUM` is not reliably URL-only. | Holdout `MEDIUM` recall is 51.16%; Kotlin tests document that a reward-event URL may lean `MEDIUM` but remain uncertain. | Treat uncertain `MEDIUM` cases as fallback candidates. |
| One global threshold was too brittle. | A boundary case in one label affected confidence for other labels. | Use per-label thresholds. |

## 6. Current Model Definition

Current model type:

```text
nearest-centroid URL classifier
```

Runtime behavior:

```text
URL
  -> deterministic feature vector
  -> min/max scaling from model artifact
  -> nearest label centroid
  -> per-label distance threshold
  -> CERTAIN verdict or UNKNOWN fallback
```

Files involved:

| File | Role |
| :-- | :-- |
| `model/training/url_features.py` | Python feature extraction |
| `model/training/train_baseline.py` | rule-scoring baseline |
| `model/training/train_model.py` | model training, cross-validation, threshold calibration, report generation |
| `model/artifacts/url_risk_centroid_model.json` | generated model parameters |
| `android/app/src/main/kotlin/com/guardian/ondevice/model/UrlFeatureExtractor.kt` | Kotlin runtime feature extraction |
| `android/app/src/main/kotlin/com/guardian/ondevice/model/OnDeviceModelJudge.kt` | Kotlin runtime inference |

## 7. Next Experiments

| Priority | Experiment | Reason |
| :-- | :-- | :-- |
| 1 | Load Kotlin model parameters directly from `url_risk_centroid_model.json`. | Remove manual duplication between Python artifact and Kotlin runtime. |
| 2 | Add Python/Kotlin feature parity tests. | Prevent training/runtime feature drift. |
| 3 | Replace min-max scaling with log scaling or percentile clipping. | Reduce long tracking URL sensitivity. |
| 4 | Add redirect and ad-network features. | Improve `LOW` vs `MEDIUM` behavior on real ad traffic. |
| 5 | Revisit `MEDIUM` and `UNKNOWN` labeling policy. | Separate true uncertainty from suspicious-but-not-severe flows. |
