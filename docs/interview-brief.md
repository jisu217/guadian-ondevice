# Interview Brief

This is a short presentation aid. The full technical explanation is in
[Project Report](project-report.md).

## One-liner

I refactored the GUADIAN risk-judgment idea into a local-first Kotlin judgment
engine that classifies ad landing URLs locally first and uses remote fallback
only when local evidence is insufficient.

## 30-second Version

The project handles risky ad landing URLs with a conservative pipeline:
deterministic rules, a lightweight URL-feature model, local policy retrieval,
and remote fallback. I implemented the Kotlin judgment engine, Python model
training/evaluation workflow, sanitized dataset, FastAPI fallback server, and
a minimal Android app (URL input -> Verdict screen) that runs the full
pipeline on an emulator, including safe failure handling when the remote
fallback is unreachable.

## Technical Highlights

- Built a `Rule -> Model -> RAG -> Remote` judgment pipeline.
- Designed an explainable `Verdict` contract with risk, reason, advice, source,
  confidence, and type.
- Built a nearest-centroid URL classifier for local inference.
- Compared the model against a rule-scoring baseline.
- Added 5-fold cross-validation, macro F1, label-level metrics, confusion
  matrix, and error analysis.
- Documented reference sources and dataset provenance.

## Current Model Results

| Metric | Value |
| :-- | --: |
| Dataset size | 668 samples |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy | 68.94% |
| Cross-validation macro F1 | 82.70% |

Main finding: `HIGH` risk is relatively strong, but `MEDIUM` and `UNKNOWN` are
hard with URL-only features because many cases require page-level context.

## What I Would Say Carefully

Use:

> I built a local inference prototype and verified it end-to-end on an Android
> emulator — rule engine, on-device model, local RAG, and remote fallback all
> exercised through a real Verdict screen.

Avoid:

> I shipped a production Android app.

Reason: emulator-only (no physical device tested), no Shield/browser
interception feature, and the UI is a bare-bones demo screen (no styling,
history, or app icon).

## Best Follow-up Answer

If asked why model accuracy dropped after adding realistic data:

> The drop was useful. Synthetic-only data made the model look stronger than it
> was. Real ad landing URLs exposed the limits of URL-only inference, especially
> for medium-risk flows that require page text, form detection, or rendered
> content. I used that result to separate model issues from data/context limits
> and to define the next engineering steps.
