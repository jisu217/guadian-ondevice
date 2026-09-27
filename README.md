# GUADIAN On-device

![Status](https://img.shields.io/badge/status-in_progress-111111)

「GUADIAN」 is a digital-harm prevention service against malicious ads, built at
the SKT FLY AI bootcamp. This repository focuses on the judgment stage of
「GUADIAN」's detect-judge-intervene flow, turning the original server-centered
risk judgment structure into a local-first hybrid on-device design.

The hybrid on-device structure is as follows. Each stage returns its verdict
when it is sufficiently certain; otherwise, it passes the request to the next
stage. ① **Rule Engine** performs clear rule-based judgment. ② **Local Verdict
Store** reuses previous judgments for the same URL; it runs after the rules so a
cached verdict never skips them. ③ **On-device Model** performs the first
local inference with a lightweight URL-feature model. ④ **Local RAG** is planned
as an additional local evidence retrieval layer. ⑤ **Page Content Inspection**
checks rendered-page signals to supplement risks that URL-only layers miss.
⑥ **Remote Fallback** asks the server only when local judgment remains difficult.
⑦ **Verdict** combines the preceding results into the final risk decision.

- [history.md](reference/original-guadian/docs/history.md) — original 「GUADIAN」 project history
- [model-evolution.md](model/docs/model-evolution.md) — model improvement history

Every major change is recorded with its measured effect.

## 1. Performance History

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

**v6–v7, feature changes.** v6 applies `log1p` to URL, host, path,
and query lengths before min-max scaling, so very long tracking URLs no longer
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

**v8-prep, `.test` leak removed (current).** v7 treated the `.test` TLD as
suspicious, but the synthetic rows use the reserved `.test` TLD and 66 of the
107 `HIGH` rows are `.test` hosts, so the feature partly learned "synthetic"
instead of "risky". v8-prep removes it and retrains on the same data and
split; the `v8` label is reserved for the model after the remaining open
issues are addressed.

| Variant | Holdout acc. | Holdout macro F1 | `HIGH` F1 | Synthetic acc. (60) | Non-synthetic acc. (72) | CV acc. | CV macro F1 |
| :-- | --: | --: | --: | --: | --: | --: | --: |
| v7 | 79.55% | 80.98% | 93.02% | 93.33% | 68.06% | 81.19% | 82.70% |
| v8-prep | 75.00% | 74.63% | 75.68% | 85.00% | 66.67% | 80.44% | 81.44% |

Most of the drop is on synthetic rows, where the leaked signal lived; on
non-synthetic rows the change is 1.39 points.

Sources: [Model Evolution](model/docs/model-evolution.md),
[Trained Model Report](model/evaluation/trained-model-report.md) (v1 `MEDIUM`
recall from its baseline column).

## 2. Current Results

Holdout results per label:

| Label | Precision | Recall | F1 |
| :-- | --: | --: | --: |
| `LOW` | 85.37% | 74.47% | 79.55% |
| `MEDIUM` | 75.61% | 70.45% | 72.94% |
| `HIGH` | 87.50% | 66.67% | 75.68% |
| `UNKNOWN` | 55.88% | 95.00% | 70.37% |

`HIGH` precision stays high but recall is 66.67%; most misses are `.apk` URLs,
which `ApkRule` resolves before the model in the app pipeline. `MEDIUM` often
needs page-level evidence, and `UNKNOWN` is overused. On the 72 non-synthetic
holdout rows alone, accuracy is 66.67%.

## 3. How It Works

```text
Ad / link destination candidate
  ↓
Rule Engine
  ↓
Local Verdict Store
  ↓
On-device Model              | model improving
  ↓
Local RAG / local evidence   | RAG planned
  ↓
Page Content Inspection
  ↓
Remote Fallback
  ↓
Verdict
```

This flow corresponds to the judgment stage of 「GUADIAN」's
detect-judge-intervene structure. Its input is a destination candidate, provided
in the current implementation by manual URL entry (`JudgeActivity`) or a
supported browser link click (`ShieldAccessibilityService`).

A stage returns its verdict if `CERTAIN`; otherwise the request passes to the
next stage. `RemoteJudge` is the last resort. The local pipeline combines URL
rules, a URL-feature nearest-centroid model, local evidence, and rendered-page
inspection before using the remote fallback. See [Architecture](docs/architecture.md).

Each stage name above maps to one runtime class. The judgment engine is the
pure-Kotlin `core/judge` module and page inspection is the Android
`core/pagecontent` module; file paths are under
`<module>/src/main/kotlin/com/guadian/ondevice/`:

| Stage | Class | Module | File |
| :-- | :-- | :-- | :-- |
| Rule Engine | `RuleBasedJudge` (`ApkRule`, `BrandImpersonationRule`, `SuspiciousUrlRule`) | `core/judge` | `rules/` |
| Local Verdict Store | `InMemoryRiskStore` (run as `StoreJudge`) | `core/judge` | `store/InMemoryRiskStore.kt` |
| On-device Model | `OnDeviceModelJudge` | `core/judge` | `classifier/OnDeviceModelJudge.kt` |
| Local RAG / local evidence | `LocalKnowledgeJudge` (keyword retrieval today) | `core/judge` | `knowledge/LocalKnowledgeJudge.kt` |
| Page Content Inspection | `PageContentJudge` | `core/pagecontent` | `pagecontent/PageContentJudge.kt` |
| Remote Fallback | `RemoteJudge` | `core/judge` | `remote/RemoteJudge.kt` |
| Verdict | `Verdict`, assembled by `HybridJudge` | `core/judge` | `judge/` |

### 3.1 Local Resolution

On the 132-row holdout split, local layers resolve **54 cases (40.91%)** before
page-content inspection or remote fallback; **34** match the dataset label.

| Resolved by | Resolved | Correct |
| :-- | --: | --: |
| Deterministic rules | 29 | 17 |
| On-device model | 9 | 7 |
| Local knowledge | 16 | 10 |
| **Total local** | **54** | **34** |

`PageContentJudge` is excluded (needs a real WebView). Source:
[Local Resolution Report](model/evaluation/local-resolution-report.md). These are
evaluation-set numbers, not production estimates.

## 4. Current Scope

End-to-end protection effect and latency in real user environments require
separate validation across Shield detection, page loading, and fallback-server
response.

Physical-device validation, production fallback deployment, and broader Shield
coverage remain future work.
