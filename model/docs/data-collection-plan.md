# Data Collection Plan

This document defines how URL samples should be expanded beyond the seed fixture.

## Current Seed Mix

`model/data/sample_urls.csv` is intentionally safe to commit. It mixes:

| Source | Purpose |
| :-- | :-- |
| `synthetic` | Controlled examples for feature and pipeline testing |
| `reference-derived` | Synthetic URLs derived from GUADIAN risk categories |
| `public-benign` | Public official or benign URLs for LOW samples |

The seed fixture is useful for pipeline validation and first classifier experiments, not for real-world performance claims.

## Expansion Targets

| Stage | Target | Purpose |
| :-- | :-- | :-- |
| Seed | 120 samples, 30 per label | First balanced training experiment |
| Prototype | 400 samples, 100 per label | Compare trained model against baseline |
| Realistic | 1000+ reviewed samples | Evaluate generalization and edge cases |

## Collection Rules

- Keep committed data sanitized and reviewable.
- Do not commit raw browsing history, production logs, user identifiers, access tokens, or private URLs.
- Use `reference-derived` for URLs reconstructed from policy categories, not copied from real incidents.
- Use `public-benign` only for official or clearly benign public URLs.
- Treat public threat feeds as external inputs: verify license, do not browse URLs, and avoid committing raw active malicious URLs.

## Review Checklist

Before adding rows:

- The label is one of `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN`.
- `source`, `reason`, and `category` are filled.
- The URL is not a duplicate or near-duplicate of an existing row.
- Query parameters are redacted if they could contain identifiers.
- The sample adds a new pattern or improves label balance.
