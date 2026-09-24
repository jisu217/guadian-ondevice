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
training/evaluation workflow, sanitized dataset, and FastAPI fallback server.
The current result is not a complete Android app yet; it is a Kotlin/JVM local
engine designed for Android integration.

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
| Dataset size | 648 samples |
| Holdout accuracy | 72.66% |
| Holdout macro F1 | 74.24% |
| Baseline accuracy | 68.75% |
| Cross-validation macro F1 | 74.51% |

Main finding: `HIGH` risk is relatively strong, but `MEDIUM` and `UNKNOWN` are
hard with URL-only features because many cases require page-level context.

## What I Would Say Carefully

Use:

> I built a local inference prototype for Android integration.

Avoid:

> I completed a production Android on-device model.

Reason: the current project has Kotlin/JVM tests and a local runtime, but no
device/emulator end-to-end validation yet.

## Best Follow-up Answer

If asked why model accuracy dropped after adding realistic data:

> The drop was useful. Synthetic-only data made the model look stronger than it
> was. Real ad landing URLs exposed the limits of URL-only inference, especially
> for medium-risk flows that require page text, form detection, or rendered
> content. I used that result to separate model issues from data/context limits
> and to define the next engineering steps.
