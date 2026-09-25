# GUADIAN On-device

[![CI](https://github.com/jisu217/guadian-ondevice/actions/workflows/ci.yml/badge.svg)](https://github.com/jisu217/guadian-ondevice/actions/workflows/ci.yml)
![Status](https://img.shields.io/badge/status-active%20development-brightgreen)

Study project that moves part of the SKT FLY AI bootcamp GUADIAN project's
URL-risk judgment onto the Android device. Deterministic URL rules, a compact
non-LLM model, local policy knowledge, and page-content inspection run before a
remote fallback, to increase locally completed judgment and reduce remote-server
calls.

> [!NOTE]
> This is a personal study project in active development. The on-device
> pipeline and model run locally and pass CI; physical-device validation and
> server deployment are still in progress.

- [x] On-device judgment pipeline (rules, model, local knowledge, page content)
- [x] URL-feature model with Python/Kotlin parity tests (current: v7)
- [x] CI for model, server, and Kotlin tests
- [ ] Remote fallback server deployment
- [ ] Physical-device validation
- [ ] Redirect-chain resolution and `MEDIUM`/`UNKNOWN` labeling revisit

## 1. Performance History

Except v2, every row is measured on the same 668-row dataset and the same
132-row holdout test split, so those numbers are directly comparable. CV is
5-fold cross-validation on the 536-row training split.

| Stage | Change | Dataset | Accuracy | Holdout macro F1 | `MEDIUM` recall | CV acc. |
| :-- | :-- | :-- | --: | --: | --: | --: |
| v1 | rule-scoring baseline | 668 rows | 68.94% | — | 50.00% | — |
| v2 | nearest-centroid classifier | synthetic only | 97.50% | — | — | — |
| v3–v5 | real ad URLs added, per-label thresholds | 668 rows | 72.73% | 74.15% | 50.00% | 73.16% |
| v6 | `log1p` on URL length features | 668 rows | 79.55% | 80.98% | 70.45% | 79.69% |
| v6 + v7 (current) | tracking and nested-URL features | 668 rows | 79.55% | 80.98% | 70.45% | 81.19% |

**v1** sums hand-written feature scores (APK, login, brand, event, free,
shortener, suspicious TLD) into a risk label. It is kept as the comparison
baseline.

**v2** trained a nearest-centroid classifier on balanced synthetic and
policy-derived samples only. Its 97.50% comes from that overly controlled
dataset, not the 668-row set, so it is not comparable to the other rows; the
drop to v3 reflects harder, more realistic data rather than a worse model.

**v3** added 248 real ad landing URLs from `reference/gold_251.jsonl` plus
banking, short-link, APK, and Korean-domain edge cases, growing the dataset from
400 to 668 rows. This became the evaluation basis. **v4–v5** replaced one shared
confidence threshold with a separate threshold per label, so a boundary problem
in one label no longer suppressed confidence for well-separated `HIGH` cases.

**v6** compresses URL, host, path, and query lengths with `log1p` before
scaling, so very long tracking URLs no longer dominate. **v7** adds query count,
UTM/tracking parameter count, nested URL, encoded URL, ad-click host, and
base64-like token features. v6 and v7 were committed together; the v6 row comes
from an ablation run that disables the v7 features. It shows that v6 accounts
for the whole holdout gain, while v7 raises CV accuracy from 79.69% to 81.19%.

Sources: [Model Evolution](model/docs/model-evolution.md) and
[Trained Model Report](model/evaluation/trained-model-report.md) (the v1
`MEDIUM` recall is its rule-baseline column).

## 2. Current Results

Per-label results of the current model on the holdout split:

| Label | Precision | Recall | F1 |
| :-- | --: | --: | --: |
| `LOW` | 85.37% | 74.47% | 79.55% |
| `MEDIUM` | 77.50% | 70.45% | 73.81% |
| `HIGH` | 90.91% | 95.24% | 93.02% |
| `UNKNOWN` | 65.52% | 95.00% | 77.55% |

`HIGH` is strongest on clear APK and impersonation-like URL patterns. `MEDIUM`
is the weakest label because many cases need page-level evidence, and `UNKNOWN`
is overused.

## 3. How It Works

```text
Browser link click
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

Each stage resolves the request when its result is `CERTAIN`; otherwise the
request falls through to the next stage, with `RemoteJudge` as the final
fallback. The model is a URL-feature nearest-centroid classifier, not a
fine-tuned LLM. Details are in [Architecture](docs/architecture.md).

### 3.1 Local Resolution

On the 132-row holdout split (not seen in training), the JVM-runnable local
layers resolve **53 cases (40.15%)** before page-content inspection or remote
fallback; **34 of those 53** match the dataset label.

| Resolved by | Resolved | Correct |
| :-- | --: | --: |
| Deterministic rules | 29 | 17 |
| On-device model | 8 | 7 |
| Local knowledge | 16 | 10 |
| **Total local** | **53** | **34** |

`PageContentJudge` is excluded because it needs a real WebView. The numbers come
from the [Local Resolution Report](model/evaluation/local-resolution-report.md),
generated by `LocalResolutionReportTest`, and are an evaluation-set
measurement, not an estimate of production traffic or real-world remote-call
reduction.

## 4. Limitations

- Shield is limited to supported browser navigation surfaces.
- The model dataset is small and partly synthetic.
- Reported evaluation results are not production security guarantees.
