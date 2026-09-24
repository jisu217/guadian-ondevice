# Data Collection Plan

This document describes how the model dataset should grow while remaining safe
to commit and honest about label provenance.

`model/data/sample_urls.csv` is a prototype dataset for feature, training, and
pipeline validation. It is not a production traffic dataset.

## Current Mix

| Source | Role |
| :-- | :-- |
| `synthetic` | controlled patterns for feature and pipeline tests |
| `reference-derived` | reconstructed or imported samples from GUADIAN-related references |
| `public-benign` | public official/normal URLs for `LOW` examples |

Current size:

| Stage | Target | Purpose | Status |
| :-- | :-- | :-- | :-- |
| Seed | 120 samples, 30 per label | first balanced experiment | done |
| Prototype | 400 samples, 100 per label | compare baseline and trained model | done |
| Realistic | 1000+ reviewed samples | evaluate generalization and edge cases | in progress: 648 samples |

## Realistic Reference Rows

The realistic stage started by merging 248 usable rows from:

```text
reference/gold_251.jsonl
```

Original source:

```text
guadian23-model-compare-main/gold/gold_251.jsonl
```

Import notes:

- 251 original rows
- 248 merged rows
- 2 malformed rows excluded
- 1 duplicate excluded
- row-level provenance preserved in `reviewed_by`

The realistic rows are valuable because they exposed model weaknesses that were
hidden by synthetic-only data. Accuracy dropped from 97.50% on synthetic-only
evaluation to 72.66% after realistic rows were included. This is treated as a
useful finding, not a failure. See [Project Report](../../docs/project-report.md)
for the evaluation interpretation.

## Collection Rules

- Keep data sanitized and safe to commit.
- Do not commit raw browsing history, private URLs, or production logs.
- Redact identifiers such as sessions, account IDs, phone numbers, and tokens.
- Do not commit raw active-malicious URL feeds without license and safety review.
- Prefer new patterns over near-duplicates.
- Preserve whether labels came from human review, rule policy, or LLM consensus.

## Review Checklist

- [ ] `label` is one of `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN`.
- [ ] `source`, `reason`, and `category` are filled.
- [ ] Query parameters are sanitized when they may contain identifiers.
- [ ] The row adds a new pattern or improves label balance.
- [ ] LLM-consensus rows are not presented as fully human-audited ground truth.

## Expansion Priorities

1. Add more human-reviewed real ad landing URLs.
2. Increase `MEDIUM` examples with clear page-level rationale.
3. Add benign tracking-heavy URLs to reduce false positives on normal ad flows.
4. Add redirect/ad-network cases with explicit categories.
5. Keep `HIGH` examples diverse, since realistic ad traffic may contain few
   obvious high-risk samples.
