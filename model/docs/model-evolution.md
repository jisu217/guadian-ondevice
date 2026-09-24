# Model Evolution

This document records how the local URL-risk model evolved. It is separate from
the generated evaluation reports: the reports show the latest numbers, while
this file explains why the current model shape was chosen.

## 1. Model Evolution

| Version | Approach | Measured issue | Result |
| :-- | :-- | :-- | :-- |
| v1 rule-scoring baseline | Hand-written feature scores from `train_baseline.py`. APK, login, brand, event, free, shortener, and suspicious TLD signals are summed into a risk label. | Easy to explain, but too coarse for mixed ad URLs. On the full 668-sample dataset it reaches 68.41% accuracy. On the final holdout split it reaches 68.94%. | Kept as the comparison baseline. |
| v2 synthetic-only centroid | Nearest-centroid classifier trained on balanced synthetic/policy-derived samples. | Synthetic-only evaluation reached 97.50% accuracy, but the dataset was too controlled and did not represent real ad landing URLs. | Not used as the final claim; treated as an over-optimistic prototype checkpoint. |
| v3 realistic data merge | Added 248 usable real ad landing references from `reference/gold_251.jsonl`, then added targeted banking, short-link, APK, and Korean-domain edge cases, producing 668 committed samples. | Holdout accuracy is 79.55% and macro F1 is 80.98% after log-compressing length features. This still shows the main weakness: `MEDIUM` and `UNKNOWN` often require signals beyond URL text. | Adopted as the current evaluation basis because it is more realistic. |
| v4 global confidence threshold | Used one shared distance threshold for all labels. | A close boundary case in one label could suppress confidence for unrelated labels. In practice, a LOW/MEDIUM boundary issue collapsed confidence for `HIGH` cases that were otherwise well separated. | Replaced. |
| v5 per-label confidence thresholds | Calibrates a separate distance threshold for each predicted label using 5-fold cross-validation on the training split. | `HIGH` confidence recovered, but `MEDIUM` still needed better separation from long benign ad/tracking URLs. | Kept. |
| v6 log-compressed length features | Applies `log1p` compression to URL, host, path, and query lengths before scaling. | Very long tracking URLs no longer dominate the min-max scale. Holdout accuracy improved to 79.55%, macro F1 to 80.98%, and `MEDIUM` recall to 70.45%. | Kept. |
| v7 redirect/ad-network URL features | Adds query count, UTM/tracking parameter count, nested URL, encoded URL, ad-click host, and base64-like token features. | Holdout accuracy stayed at 79.55%, while 5-fold CV improved to 81.19% accuracy and 82.70% macro F1. Error buckets now separate benign tracking over-risk from context-dependent `MEDIUM` misses. | Current approach. |

## 2. Dataset Milestones

| Stage | Samples | Composition | Role |
| :-- | --: | :-- | :-- |
| Seed | 120 | 30 samples per label | initial feature and pipeline validation |
| Prototype | 400 | synthetic, policy-derived, and public-benign rows | baseline vs trained-model comparison |
| Realistic | 668 | prototype rows plus 248 real ad landing references and targeted edge cases | current evaluation basis |

The realistic stage intentionally lowered headline accuracy. The drop is useful:
it shows that the model is being evaluated against cases closer to actual ad
traffic instead of only controlled synthetic patterns.

The committed 668-row dataset is not a single-source corpus. It currently
contains 301 synthetic rows, 275 reference-derived rows, and 92 public-benign
rows. The largest imported local source is `reference/gold_251.jsonl`, which
contributed 248 usable real ad landing references.

## 3. Current Metrics

Latest trained-model report:

| Metric | Value |
| :-- | --: |
| Train samples | 536 |
| Test samples | 132 |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy on same split | 68.94% |
| 5-fold CV accuracy | 81.19% |
| 5-fold CV macro F1 | 82.70% |

Holdout label metrics:

| Label | Precision | Recall | F1 | Interpretation |
| :-- | --: | --: | --: | :-- |
| `LOW` | 85.37% | 74.47% | 79.55% | acceptable, but long ad/tracking URLs still cause misses |
| `MEDIUM` | 77.50% | 70.45% | 73.81% | weakest label; often needs page-level evidence |
| `HIGH` | 90.91% | 95.24% | 93.02% | strongest label; clear APK and impersonation-like patterns work well |
| `UNKNOWN` | 65.52% | 95.00% | 77.55% | intentionally conservative, but overused |

## 4. Confidence Calibration

The current model does not return every nearest-centroid label as a confident
local verdict. It uses calibrated distance thresholds:

| Label | Threshold |
| :-- | --: |
| `LOW` | 0.4395 |
| `MEDIUM` | 0.9580 |
| `HIGH` | 1.0449 |
| `UNKNOWN` | 0.4040 |

This matters because the local model sits inside a hybrid safety pipeline.
If the nearest centroid is not confident enough, the result falls through to
local RAG or remote fallback instead of forcing a local decision.

Current holdout error buckets:

| Bucket | Count |
| :-- | --: |
| `LOW` benign URL lacks enough local context | 7 |
| `MEDIUM` needs page or interaction context | 7 |
| `MEDIUM` under-risked by URL-only signal | 6 |
| `LOW` tracking/ad URL over-risked | 5 |
| `HIGH` impersonation signal below threshold | 1 |
| `UNKNOWN` over-classified by sparse URL signal | 1 |

## 5. Key Findings

| Finding | Evidence | Engineering decision |
| :-- | :-- | :-- |
| Synthetic-only results overstated quality. | Accuracy fell from 97.50% to 79.55% after realistic rows, edge cases, and current feature scaling were added. | Keep realistic rows in the main evaluation set. |
| Rules remain useful as a baseline. | Full-dataset baseline accuracy is 68.41%; holdout baseline accuracy is 68.94%. | Keep `train_baseline.py` for comparison and sanity checks. |
| `HIGH` is learnable from URL features. | Holdout `HIGH` recall is 95.24%, F1 is 93.02%. | Let the model short-circuit confident high-risk URL patterns. |
| `MEDIUM` improved but remains context-sensitive. | Holdout `MEDIUM` recall improved to 70.45% after log-compressed length features, but many medium-risk cases still need page content or redirect context. | Treat uncertain `MEDIUM` cases as fallback candidates. |
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
  -> load generated JSON artifact
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
| `app/src/main/kotlin/com/guardian/ondevice/model/UrlFeatureExtractor.kt` | Kotlin runtime feature extraction |
| `app/src/main/kotlin/com/guardian/ondevice/model/OnDeviceModelJudge.kt` | Kotlin runtime inference |

## 7. Completed Runtime Improvement

| Change | Reason | Result |
| :-- | :-- | :-- |
| Kotlin artifact loading | Remove manual duplication between Python artifact and Kotlin runtime. | `NearestCentroidUrlModel.default()` now reads `url_risk_centroid_model.json` from the classpath resource. |
| Python/Kotlin feature parity fixture | Prevent training/runtime feature drift. | `feature_parity_cases.csv` is checked by both Python and Kotlin tests. |
| Log-compressed length features | Prevent very long ad/tracking URLs from stretching feature scale. | Python training and Kotlin inference both apply the same compression before distance calculation. |
| Redirect/ad-network features | Make tracking-heavy URLs explicit instead of hiding them inside raw length. | Python and Kotlin extract the same redirect/ad-click features and cover them with parity fixtures. |
| `PageContentJudge` (page-level signals) | URL-only features still missed form, brand wording, and rendered-content evidence. | Renders the destination URL in a hidden on-device WebView after Rule/Model/RAG are uncertain, and flags an unverified login form or a page/host brand mismatch. Does not change the URL-feature model itself. |
| `ShieldAccessibilityService` | `PageContentJudge` and the rest of the pipeline only ran against a URL entered by hand. | Detects an off-site link click (or one inside an iframe) across 6 browsers and runs the same `HybridJudge` pipeline automatically, behind a full-screen on-device overlay. Site detection infers the current page's site from its own top-level links (`inferCurrentSiteHost`), not a per-browser address-bar resource id -- the original GUADIAN's `WebNode.kt` documents the address bar disappearing from the tree when scrolled, which is why this approach was ported instead. Manually verified on emulator, including the negative case (same-site click produces no overlay). All `AccessibilityNodeInfo`-dependent logic -- including the tree-walking functions (`inferCurrentSiteHost`, `isInsideNestedDoc`) -- is additionally covered by Robolectric (`WebAccessibilityNodeRobolectricTest`), using its `Shadows.shadowOf(parent).addChild(child)` test API to build real parent/child node trees instead of the default no-op stub. Only the service's real-browser event wiring remains manual-only -- see docs/architecture.md. |

## 8. Next Experiments

| Priority | Experiment | Reason |
| :-- | :-- | :-- |
| 1 | Add real redirect-chain resolution. | Current features can detect nested URLs, but they do not follow the actual HTTP chain. |
| 2 | Revisit `MEDIUM` and `UNKNOWN` labeling policy. | Separate true uncertainty from suspicious-but-not-severe flows. |
| 3 | Extend Shield beyond Chrome/Samsung Internet, and to ads rendered inside another app's own WebView. | Currently only two browsers are recognized; in-app ad content (not a browser tab) is not covered at all. |

## 9. Data Collection Rules

When adding or changing rows in `model/data/sample_urls.csv`:

- Keep data sanitized and safe to commit; never commit raw browsing history,
  private URLs, or production logs.
- Redact identifiers such as sessions, account IDs, phone numbers, and tokens.
- Do not commit raw active-malicious URL feeds without a license and safety
  review.
- Prefer new patterns over near-duplicates, and preserve whether a label came
  from human review, rule policy, or LLM consensus.

Review checklist for a new row:

- [ ] `label` is one of `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN`.
- [ ] `source`, `reason`, and `category` are filled.
- [ ] Query parameters are sanitized when they may contain identifiers.
- [ ] The row adds a new pattern or improves label balance.
- [ ] LLM-consensus rows are not presented as fully human-audited ground truth.

Collection priorities, in order: more human-reviewed real ad-landing URLs;
more `MEDIUM` examples with a clear page-level rationale; more benign
tracking-heavy URLs to reduce false positives on normal ad flows; more
redirect/ad-network cases with explicit categories; and keeping `HIGH`
examples diverse, since realistic ad traffic surfaces few obvious high-risk
samples.
