# Model Workspace

This directory contains the URL-risk model dataset, feature extraction code,
training scripts, generated artifacts, and evaluation reports.

The model is a compact URL-feature classifier for local inference. It is not a
fine-tuned LLM and it does not fetch page content. Its role is to provide a
fast local signal inside the broader `Rule -> Model -> RAG -> Remote` pipeline.

## Directory Structure

| Path | Purpose |
| :-- | :-- |
| `data/sample_urls.csv` | sanitized training/evaluation samples |
| `data/official_domains.txt` | Tranco top 50000 + project seed/brand domains |
| `docs/dataset-schema.md` | CSV schema and labeling rules |
| `docs/feature-spec.md` | Python/Kotlin feature contract |
| `docs/model-development-history.md` | model experiment history and design decisions |
| `training/url_features.py` | Python feature extractor |
| `training/train_baseline.py` | rule-scoring baseline |
| `training/train_model.py` | nearest-centroid training and evaluation |
| `training/build_official_domains.py` | official-domain allowlist builder |
| `artifacts/url_risk_centroid_model.json` | generated model parameters |
| `evaluation/trained-model-report.md` | generated model report |

## Dataset

Current committed dataset:

| Source | Count |
| :-- | --: |
| Synthetic and policy-derived samples | 400 |
| Real ad landing references | 248 |
| Total | 648 |

The real ad landing rows are derived from `reference/gold_251.jsonl`. Row-level
provenance is recorded through `source` and `reviewed_by`. See
[Source And Reference Notes](../docs/source-and-reference.md) for details.

## Current Results

Latest trained-model report:

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 72.66% |
| Holdout macro F1 | 74.24% |
| Baseline accuracy | 68.75% |
| Cross-validation accuracy | 72.52% |
| Cross-validation macro F1 | 74.51% |

The realistic rows lowered headline accuracy but revealed useful failure modes:
`HIGH` is strong, while `MEDIUM` and `UNKNOWN` need better page-context or
redirect/ad-network features.

## Commands

Run model tests:

```bash
python3 -m unittest discover model/training
```

Train and regenerate report/artifact:

```bash
python3 model/training/train_model.py
```

Run the baseline report:

```bash
python3 model/training/train_baseline.py
```

Rebuild the official-domain allowlist:

```bash
python3 model/training/build_official_domains.py --tranco /path/to/top-1m.csv
```

The Tranco input file is intentionally not committed.

## Data Policy

- Commit sanitized fixtures only.
- Do not commit raw browsing logs, private URLs, or page bodies.
- Redact identifiers such as session tokens and account IDs.
- Keep large third-party raw datasets outside the repository.
- Preserve label provenance when rows are generated from LLM consensus rather
  than full human review.

## Related Documents

- [Project Report](../docs/project-report.md)
- [Model Development History](docs/model-development-history.md)
- [Feature Specification](docs/feature-spec.md)
- [Dataset Schema](docs/dataset-schema.md)
- [Trained Model Report](evaluation/trained-model-report.md)
