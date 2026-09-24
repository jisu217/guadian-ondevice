# Project Report

## 1. Overview

GUADIAN On-device Judge is a local-first URL risk judgment prototype for
advertisement landing URLs. The project explores how much risk judgment can be
performed locally before sending a URL to a remote fallback service.

The current system is best described as:

> a Kotlin/JVM local judgment engine designed for later Android integration,
> with a Python model-training pipeline and a FastAPI remote fallback server.

It is not yet a complete Android application. No Android UI, Shield screen,
browser interception, or device-level end-to-end validation has been completed.

## 2. Problem

Ad landing URLs can lead to phishing, brand impersonation, APK downloads,
credential capture, personal information forms, or payment lures. A server-only
judge can inspect richer context, but it introduces latency, cost, and privacy
concerns because every candidate URL must be transmitted.

This project therefore asks:

1. Which URL-risk decisions can be resolved locally?
2. Which cases should remain uncertain and fall back to a stronger judge?
3. How should model confidence be measured so the local layer does not
   overclaim?

## 3. Reference Basis

The work was developed with reference to the earlier GUADIAN project and a
sibling model-comparison dataset. The reference material is stored under
`reference/` and documented in [Source And Reference Notes](source-and-reference.md).

The main points carried forward were:

- a `Judge`-style abstraction
- a verdict object with risk, reason, and advice
- risk categories such as APK downloads, brand impersonation, and suspicious
  landing flows
- the idea of a remote judge for cases that cannot be safely resolved locally

The current local-first pipeline, model training workflow, Kotlin runtime model,
local policy fallback, and FastAPI fallback server were implemented in this
repository.

## 4. Architecture

The runtime pipeline is:

```text
URL
  -> LocalRiskStore
  -> RuleBasedJudge
  -> OnDeviceModelJudge
  -> LocalRagJudge
  -> RemoteJudge
  -> Verdict
```

The sequence is conservative by design. Deterministic evidence is handled first;
the model is used only after rules are uncertain; low-confidence local results
fall through to local policy retrieval or remote fallback.

| Layer | Purpose |
| :-- | :-- |
| Local store | Reuse previous host-level verdicts |
| Rule engine | Handle clear APK, official-domain, brand, and suspicious-token cases |
| Local model | Classify URL feature vectors with a lightweight centroid model |
| Local RAG | Apply local policy snippets for known risk patterns |
| Remote fallback | Ask a server-side judge when local evidence is insufficient |

See [Architecture](architecture.md) for component-level details.

## 5. Model Development

The model is a lightweight URL-feature classifier, not a fine-tuned LLM. This
choice was intentional: the goal was to test whether an Android-suitable local
model could provide useful first-pass risk judgment without network access.

The model pipeline includes:

- sanitized URL dataset
- deterministic feature extraction
- rule-based baseline
- nearest-centroid classifier
- 5-fold stratified cross-validation
- label-level precision, recall, and F1
- confusion matrix and error analysis
- calibrated per-label confidence thresholds

Feature examples:

- URL, host, path, and query length
- digit and special-character ratios
- subdomain count
- APK, login, event, reward, and brand tokens
- official-domain match
- suspicious TLD signal

The Python extractor and Kotlin runtime extractor are kept conceptually aligned.
`model/data/official_domains.txt` is read as a shared resource to reduce
manual drift.

The model iteration history is documented in
[Model Development History](../model/docs/model-development-history.md).

## 6. Dataset

The current committed dataset is a sanitized prototype dataset, not a production
traffic corpus.

| Source | Count | Purpose |
| :-- | --: | :-- |
| Synthetic and policy-derived samples | 400 | balanced feature and pipeline validation |
| Real ad landing references | 248 | expose realistic URL patterns and model failure modes |
| Total | 648 | prototype training and evaluation |

The 248 real ad landing references were derived from a sibling gold dataset.
The dataset records whether each row came from human review or LLM-consensus
labeling. This distinction is important because LLM-consensus labels should not
be presented as equivalent to fully audited production ground truth.

## 7. Evaluation Results

Current trained-model report:

| Metric | Value |
| :-- | --: |
| Train samples | 520 |
| Test samples | 128 |
| Holdout accuracy | 72.66% |
| Holdout macro F1 | 74.24% |
| Baseline accuracy on same split | 68.75% |
| 5-fold CV accuracy | 72.52% |
| 5-fold CV macro F1 | 74.51% |

Final holdout label metrics:

| Label | Precision | Recall | F1 |
| :-- | --: | --: | --: |
| LOW | 82.50% | 73.33% | 77.65% |
| MEDIUM | 75.86% | 51.16% | 61.11% |
| HIGH | 90.48% | 95.00% | 92.68% |
| UNKNOWN | 50.00% | 95.00% | 65.52% |

Interpretation:

- `HIGH` is the strongest class. Clear APK and impersonation-like patterns are
  well represented by URL features.
- `LOW` is acceptable but affected by long tracking URLs and ad-network
  redirect URLs.
- `MEDIUM` is the weakest class. Many medium-risk cases require page-level
  context such as forms, rendered content, or offer wording.
- `UNKNOWN` has high recall but low precision, which is expected from a
  conservative fallback-oriented design.

## 8. What Changed After Adding Realistic URLs

Synthetic-only evaluation was much higher, but it overstated model quality.
After realistic ad landing URLs were added, accuracy dropped and failure modes
became visible.

The main findings were:

1. **URL-only judgment has a hard information limit.** Some medium-risk flows
   require page text, forms, or rendered behavior.
2. **A single global confidence threshold was fragile.** One ambiguous label
   boundary could suppress confident predictions for other labels.
3. **Min-max scaling is sensitive to long tracking URLs.** Very long ad URLs
   stretch feature ranges and compress normal samples.

The second issue has been addressed by calibrating confidence thresholds per
label. The first and third remain active model-development tasks.

## 9. Current Limitations

- No Android device or emulator end-to-end verification yet.
- Current build is Kotlin/JVM, not a full Android Gradle app module.
- URL-only model cannot inspect page body, forms, JavaScript rendering, or final
  redirected content.
- Dataset is still small and partly synthetic.
- Some labels come from LLM consensus rather than full human review.
- Model parameters are still embedded in the Kotlin runtime; artifact-to-runtime
  synchronization should be automated.

## 10. Next Steps

Recommended order:

1. Load model parameters directly from `url_risk_centroid_model.json` in Kotlin.
2. Add Python/Kotlin feature parity tests for shared sample URLs.
3. Improve feature robustness with log scaling or percentile clipping.
4. Add redirect/ad-network features such as encoded target URL, `gclid`, `utm`,
   base64-like parameters, and known ad-click endpoints.
5. Revisit `MEDIUM` versus `UNKNOWN` labeling policy.
6. Build the Android integration layer after the local engine contract is stable.

## 11. How To Present This Project

A precise summary:

> I refactored the GUADIAN risk-judgment idea into a local-first Kotlin
> judgment engine. I built deterministic rules, a compact URL-feature model,
> local policy fallback, a remote fallback server, and a model evaluation
> pipeline. The model is not production-grade yet, but the project now has
> measurable local performance, documented data provenance, and clear failure
> modes for future improvement.
