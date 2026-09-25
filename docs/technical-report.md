# Technical Report

This report summarizes the problem, runtime design, model, dataset, and
evaluation of GUADIAN On-device Judge. Runtime details are in
[Architecture](architecture.md); source lineage is in
[Provenance](provenance.md).

## Table of Contents

1. [Overview](#1-overview)
2. [Problem](#2-problem)
3. [Reference Basis](#3-reference-basis)
4. [Runtime](#4-runtime)
5. [Model](#5-model)
6. [Dataset](#6-dataset)
7. [Evaluation](#7-evaluation)
8. [Verification](#8-verification)
9. [Limitations](#9-limitations)
10. [Next Steps](#10-next-steps)

## 1. Overview

GUADIAN On-device Judge is a local-first Android prototype for URL-risk
judgment. It refactors the original GUADIAN ad-risk idea into an Android app
module (`app/`) that runs the local judgment pipeline, a Python model workspace
(`model/`) for training, evaluation, and the generated model artifact, and a
FastAPI fallback server (`server/`).

The app has two entry points: `JudgeActivity` for manual URL input, and
`ShieldAccessibilityService`, which detects link clicks in supported browsers
through Android accessibility APIs. As of 2026-09-24, the app builds and runs
on an emulator; physical-device validation is pending.

## 2. Problem

Ad landing URLs can lead to phishing, APK downloads, brand impersonation,
credential capture, or payment lures. A server-only judge can inspect richer
context, but it adds latency, cost, and privacy exposure. This project asks how
far a conservative on-device pipeline can go before remote fallback is needed.

## 3. Reference Basis

The project was developed with reference to the earlier GUADIAN project and a
sibling model-comparison dataset. Preserved source material is stored under
`reference/`; copied, derived, and reference-only boundaries are documented in
[Provenance](provenance.md).

The repository is a new implementation, not a direct port. The new work covers
the local-first Kotlin judgment pipeline, the generated URL-feature model
artifact, the local policy fallback, hidden-WebView page-content inspection,
the accessibility-triggered Shield flow, the FastAPI fallback server, and the
model documentation checks.

## 4. Runtime

```text
URL
  -> InMemoryRiskStore
  -> RuleBasedJudge
  -> LocalReasoningJudge
       -> OnDeviceModelJudge
       -> LocalKnowledgeJudge
       -> PageContentJudge
  -> RemoteJudge
  -> Verdict
```

The order is intentional. Cheap deterministic checks (the store and rules) run
first. The URL model's confidence is calibrated with per-label distance
thresholds, so it only answers when it is close enough to a centroid.
Rendered-page inspection is delayed until URL-only evidence is insufficient,
and the remote fallback runs only when every local layer is uncertain. Control
flow and the `Verdict` contract are described in [Architecture](architecture.md).

## 5. Model

The model is a nearest-centroid URL-feature classifier over four labels (`LOW`,
`MEDIUM`, `HIGH`, `UNKNOWN`), not a fine-tuned LLM. The four length features are
`log1p`-compressed before min-max scaling, and each label has its own
certain-distance threshold, calibrated by 5-fold cross-validation on the
training split. Kotlin loads the generated artifact
(`model/artifacts/url_risk_centroid_model.json`) directly, and Python training
and Kotlin inference share a feature contract checked by
`model/data/feature_parity_cases.csv`.

The 22 features fall into six groups:

| Group | Features |
| :-- | :-- |
| Length | `url_length`, `host_length`, `path_length`, `query_length` |
| Character composition | `digit_ratio`, `special_char_ratio` |
| Host structure | `subdomain_count` |
| Risk tokens | `has_apk`, `has_login`, `has_event`, `has_free`, `has_shortener_token`, `brand_keyword_present` |
| Domain signals | `official_domain_match`, `suspicious_tld` |
| Redirect / ad-network | `query_param_count`, `utm_param_count`, `tracking_param_count`, `has_nested_url`, `has_encoded_url`, `has_ad_click_host`, `has_base64_like_token` |

Model history is in [Model Evolution](../model/docs/model-evolution.md).

## 6. Dataset

The committed dataset, `model/data/sample_urls.csv`, holds 668 rows of
sanitized prototype data, not production traffic. It mixes three sources:

| Source | Rows | Content |
| :-- | --: | :-- |
| `synthetic` | 301 | controlled samples for feature and pipeline validation |
| `reference-derived` | 275 | 248 real ad landing rows from `gold_251.jsonl` + 27 GUADIAN-policy-derived rows |
| `public-benign` | 92 | public official/normal URLs |

By label, the dataset has 236 `LOW`, 222 `MEDIUM`, 107 `HIGH`, and 103
`UNKNOWN` rows. 217 labels come from LLM consensus (Claude, Gemini, OpenAI);
they are treated as prototype evidence, not audited incident truth. Row-level
provenance is in [Provenance §5](provenance.md#5-committed-dataset-provenance).

## 7. Evaluation

Evaluation uses a deterministic stratified holdout split of 536 training and
132 test rows, committed as `model/evaluation/train-split.csv` and
`model/evaluation/test-split.csv`. Numbers below come from
`model/evaluation/trained-model-report.md`.

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Rule baseline accuracy (same split) | 68.94% |
| 5-fold CV accuracy | 81.19% |
| 5-fold CV macro F1 | 82.70% |

Per label, `HIGH` is strongest and `MEDIUM` weakest:

| Label | Precision | Recall | F1 | Support | Interpretation |
| :-- | --: | --: | --: | --: | :-- |
| `LOW` | 85.37% | 74.47% | 79.55% | 47 | affected by tracking-heavy benign URLs |
| `MEDIUM` | 77.50% | 70.45% | 73.81% | 44 | weakest; often needs page context |
| `HIGH` | 90.91% | 95.24% | 93.02% | 21 | strongest for clear URL-level risk |
| `UNKNOWN` | 65.52% | 95.00% | 77.55% | 20 | conservative fallback-oriented label |

The holdout errors fall into these buckets:

| Bucket | Count |
| :-- | --: |
| `LOW` benign URL lacks enough local context | 7 |
| `MEDIUM` needs page or interaction context | 7 |
| `MEDIUM` under-risked by URL-only signal | 6 |
| `LOW` tracking/ad URL over-risked | 5 |
| `HIGH` impersonation signal below threshold | 1 |
| `UNKNOWN` over-classified by sparse URL signal | 1 |

Adding realistic ad-landing rows lowered headline accuracy but exposed the
limits of URL-only inference. The redirect/ad-network features left holdout
accuracy at 79.55% and raised 5-fold CV to 81.19% accuracy and 82.70% macro F1.
Page-content inspection addresses runtime blind spots that the URL model cannot
cover.

## 8. Verification

Most checks run in CI; instrumented and emulator checks are manual:

| Type | Scope | Run by |
| :-- | :-- | :-- |
| Kotlin unit tests | judges, rules, store, feature extraction, remote client | CI (`./gradlew test`) |
| Robolectric tests | accessibility-node helper logic | CI (`./gradlew test`) |
| Python model tests | training, features, allowlist build | CI |
| Server tests | FastAPI app and judge service | CI |
| Model documentation checks | `scripts/check-model-requirements.sh` | CI |
| Instrumented tests | `JudgeActivityTest`, `PageContentJudgeTest` | manual (`./gradlew connectedDebugAndroidTest`, emulator/device required) |

Manual emulator verification has covered URL judgment through `JudgeActivity`,
the safe `UNKNOWN` fallback when the remote server is unavailable, the Shield
overlay on off-site browser link clicks, and the absence of an overlay on
same-site browser navigation.

## 9. Limitations

- No physical-device validation yet.
- Shield detects candidate browser clicks; it does not prove a click is an ad.
- Arbitrary in-app WebViews and native ad surfaces are not covered.
- Page-content inspection depends on WebView loading, timeout behavior, and
  simple DOM heuristics.
- The dataset is small and partly synthetic.
- The project is a research prototype, not a production security product.

## 10. Next Steps

1. Run and document physical-device verification.
2. Improve accessibility permission onboarding and risk-result UI.
3. Add redirect-chain resolution for nested landing URLs.
4. Expand human-reviewed `MEDIUM` and benign tracking-heavy samples.
5. Revisit `MEDIUM` versus `UNKNOWN` labeling policy.
6. Decide whether nearest-centroid remains sufficient or should be replaced by
   another on-device-friendly classifier.
