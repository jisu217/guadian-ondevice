# Technical Report

## Overview

GUADIAN On-device Judge is a local-first Android prototype for URL-risk
judgment. It refactors the original GUADIAN ad-risk idea into an Android app
module, Python model workspace, and FastAPI fallback server.

The prototype has two Android entry points:

- `JudgeActivity`: manual URL input
- `ShieldAccessibilityService`: supported-browser link-click detection through
  Android accessibility APIs

As of 2026-09-24, the app builds and runs on an emulator. Physical-device
validation is still pending.

## Problem

Ad landing URLs can lead to phishing, APK downloads, brand impersonation,
credential capture, or payment lures. A server-only judge can inspect richer
context, but it adds latency, cost, and privacy exposure. This project asks how
far a conservative on-device pipeline can go before remote fallback is needed.

## Reference Basis

The project was developed with reference to the earlier GUADIAN project and a
sibling model-comparison dataset. Preserved source material is stored under
`reference/`; copied, derived, and reference-only boundaries are documented in
[Provenance](provenance.md).

The current repository is a new implementation rather than a direct port. The
new work includes the local-first Kotlin judgment pipeline, generated
URL-feature model artifact, local policy fallback, hidden-WebView page-content
inspection, accessibility-triggered Shield flow, FastAPI fallback server, and
model documentation checks.

## Runtime

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

The order is intentional: cheap deterministic checks run first, URL-model
confidence is calibrated, rendered-page inspection is delayed until local
URL-only evidence is insufficient, and remote fallback remains the last layer.

## Model

The model is a compact nearest-centroid URL-feature classifier, not a fine-tuned
LLM. Python training and Kotlin runtime extraction share a feature contract and
parity fixture. Kotlin loads the generated JSON artifact directly from
`model/artifacts/url_risk_centroid_model.json`.

Feature groups include:

- URL, host, path, and query length
- digit and special-character ratios
- subdomain count
- APK, login, event, reward, brand, and shortener tokens
- official-domain and suspicious-TLD signals
- tracking, nested URL, encoded URL, ad-click host, and base64-like token signals

Detailed model history lives in
[Model Evolution](../model/docs/model-evolution.md).

## Dataset

The committed dataset is sanitized prototype data, not production traffic.

| Source | Count | Purpose |
| :-- | --: | :-- |
| Synthetic and policy-derived samples | 420 | feature and pipeline validation |
| Real ad landing references | 248 | realistic URL patterns and failure modes |
| Total | 668 | prototype training and evaluation |

Some labels come from LLM-consensus data; they are treated as prototype
evidence, not audited incident truth.

## Evaluation

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

Holdout label metrics:

| Label | Precision | Recall | F1 | Interpretation |
| :-- | --: | --: | --: | :-- |
| `LOW` | 85.37% | 74.47% | 79.55% | affected by tracking-heavy benign URLs |
| `MEDIUM` | 77.50% | 70.45% | 73.81% | weakest; often needs page context |
| `HIGH` | 90.91% | 95.24% | 93.02% | strongest for clear URL-level risk |
| `UNKNOWN` | 65.52% | 95.00% | 77.55% | conservative fallback-oriented label |

Main finding: realistic ad-landing rows lowered headline accuracy but exposed
the true limits of URL-only inference. Redirect/ad-network features improved
cross-validation stability, while page-content inspection addresses runtime
blind spots that the URL model itself cannot own.

## Android Verification

Automated verification covers Kotlin unit tests, Robolectric tests for
accessibility-node helper logic, Python model tests, server tests, and model
documentation checks in CI.

Manual emulator verification has covered URL judgment through `JudgeActivity`,
safe `UNKNOWN` fallback when the remote server is unavailable, Shield overlay
display on off-site browser link clicks, and no overlay on same-site browser
navigation.

## Limitations

- No physical-device validation yet.
- Shield detects candidate browser clicks; it does not prove a click is an ad.
- Arbitrary in-app WebViews and native ad surfaces are not covered.
- Page-content inspection depends on WebView loading, timeout behavior, and
  simple DOM heuristics.
- Dataset is small and partly synthetic.
- The project is a research prototype, not a production security product.

## Next Steps

1. Run and document physical-device verification.
2. Improve accessibility permission onboarding and risk-result UI.
3. Add redirect-chain resolution for nested landing URLs.
4. Expand human-reviewed `MEDIUM` and benign tracking-heavy samples.
5. Revisit `MEDIUM` versus `UNKNOWN` labeling policy.
6. Decide whether nearest-centroid remains sufficient or should be replaced by
   another on-device-friendly classifier.
