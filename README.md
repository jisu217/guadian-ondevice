# GUADIAN On-device

[![CI](https://github.com/jisu217/guadian-ondevice/actions/workflows/ci.yml/badge.svg)](https://github.com/jisu217/guadian-ondevice/actions/workflows/ci.yml)
![Status](https://img.shields.io/badge/status-active%20development-brightgreen)

「GUADIAN」은 SKT FLY AI 부트캠프에서 개발한 악성 광고에서 시작되는
디지털 피해 예방 서비스이며, 본 저장소는 「GUADIAN」의 기존 서버 판정
구조를 하이브리드 온디바이스 방식으로 전환하는 구현을 담고 있다.

본 저장소의 하이브리드 온디바이스 구조는 다음과 같다. ① Local Verdict
Store는 이전 판정 결과를 확인해 반복 요청을 로컬에서 처리한다. ② Rule
Engine은 명확한 규칙 기반 판정을 먼저 수행하고, 판정이 불확실한 경우
`UNCERTAIN` 상태로 다음 단계에 넘긴다. ③ On-device Model은 URL feature를
기반으로 기기 내부에서 위험도를 추론하며, confidence가 낮거나 결과가
`UNKNOWN`인 경우 다음 단계로 넘긴다. ④ Local RAG는 로컬 근거 검색 기반의
추가 판단 단계로 확장할 예정이다. ⑤ Remote Fallback은 로컬에서 여전히
충분히 확신할 수 없는 경우 서버 판정을 요청한다. ⑥ Verdict는 앞선 단계의
결과를 종합해 최종 위험도를 결정한다.

- [history.md](reference/docs/history.md) — 「GUADIAN」 원본 프로젝트의 개발 이력
- [model-evolution.md](model/docs/model-evolution.md) — 본 저장소의 모델 개선 이력

모든 주요 변경은 측정한 효과와 함께 기록한다.

## 1. Performance History

**v1, rule-scoring baseline.** `train_baseline.py` sums hand-written feature
scores (APK, login, brand, event, free, shortener, and suspicious-TLD signals)
into a risk label. It is easy to explain but too coarse for mixed ad URLs,
reaching 68.41% accuracy on the full 668-row dataset and 68.94% on the final
holdout split. It is kept as the comparison baseline.

**v2, synthetic-only centroid.** A nearest-centroid classifier trained on
balanced synthetic and policy-derived samples reached 97.50% accuracy, but the
dataset was too controlled to represent real ad landing URLs. It is treated as
an over-optimistic checkpoint, not a final claim.

**v3, realistic data merge.** Adding 248 real ad landing rows from
`reference/gold_251.jsonl`, then targeted banking, short-link, APK, and
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

**v6–v7, feature changes (current).** v6 applies `log1p` to URL, host, path,
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
| v6 + v7 (current) | report at `a95b51b`; ablation reproduces it | 79.55% | 80.98% | 70.45% | 81.19% | 82.70% |

v6 accounts for the whole holdout gain. Adding v7 on top leaves holdout
accuracy unchanged and raises 5-fold CV from 79.69% to 81.19% accuracy and from
81.18% to 82.70% macro F1.

Sources: [Model Evolution](model/docs/model-evolution.md),
[Trained Model Report](model/evaluation/trained-model-report.md) (v1 `MEDIUM`
recall from its baseline column).

## 2. Current Results

Holdout results per label:

| Label | Precision | Recall | F1 |
| :-- | --: | --: | --: |
| `LOW` | 85.37% | 74.47% | 79.55% |
| `MEDIUM` | 77.50% | 70.45% | 73.81% |
| `HIGH` | 90.91% | 95.24% | 93.02% |
| `UNKNOWN` | 65.52% | 95.00% | 77.55% |

`HIGH` is strongest (clear APK and impersonation patterns). `MEDIUM` is weakest,
since many cases need page-level evidence. `UNKNOWN` is overused.

## 3. How It Works

```text
Browser link click
  -> ShieldAccessibilityService
  -> Local Verdict Store
  -> Rule Engine
  -> On-device Model
  -> Local RAG / local evidence
  -> Page Content Inspection
  -> Remote Fallback
  -> Verdict
```

A stage returns its verdict if `CERTAIN`; otherwise the request passes to the
next stage. `RemoteJudge` is the last resort. The model is a URL-feature
nearest-centroid classifier, not an LLM. See [Architecture](docs/architecture.md).

### 3.1 Local Resolution

On the 132-row holdout split, local layers resolve **53 cases (40.15%)** before
page-content inspection or remote fallback; **34** match the dataset label.

| Resolved by | Resolved | Correct |
| :-- | --: | --: |
| Deterministic rules | 29 | 17 |
| On-device model | 8 | 7 |
| Local knowledge | 16 | 10 |
| **Total local** | **53** | **34** |

`PageContentJudge` is excluded (needs a real WebView). Source:
[Local Resolution Report](model/evaluation/local-resolution-report.md). These are
evaluation-set numbers, not production estimates.

## 4. Limitations

- Browser-triggered Shield coverage is limited to supported navigation surfaces.
- The current dataset is small and includes synthetic and reference-derived rows.
- Physical-device validation and production fallback-server deployment remain
  future work.
