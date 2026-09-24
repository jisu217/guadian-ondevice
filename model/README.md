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
| `data/feature_parity_cases.csv` | shared Python/Kotlin feature parity fixture |
| `docs/dataset-schema.md` | CSV schema and labeling rules |
| `docs/feature-spec.md` | Python/Kotlin feature contract |
| `docs/model-evolution.md` | model experiment history and design decisions |
| `training/url_features.py` | Python feature extractor |
| `training/train_baseline.py` | rule-scoring baseline |
| `training/train_model.py` | nearest-centroid training and evaluation |
| `training/build_official_domains.py` | official-domain allowlist builder |
| `artifacts/url_risk_centroid_model.json` | generated model parameters read by the Kotlin runtime |
| `evaluation/trained-model-report.md` | generated model report |
| `evaluation/model-card.md` | model role, metrics, intended use, and limitations |

## Dataset

Current committed dataset:

| Source | Count |
| :-- | --: |
| Synthetic and policy-derived samples | 420 |
| Real ad landing references | 248 |
| Total | 668 |

The real ad landing rows are derived from `reference/gold_251.jsonl`. Row-level
provenance is recorded through `source` and `reviewed_by`. See
[Provenance](../docs/provenance.md) for details.

## Current Results

Latest trained-model report:

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy | 68.94% |
| Cross-validation accuracy | 81.19% |
| Cross-validation macro F1 | 82.70% |

The realistic rows lowered headline accuracy but revealed useful failure modes:
`HIGH` is strong, while `MEDIUM` and `UNKNOWN` need better page-context or
redirect/ad-network features.

## Commands

Run model tests:

```bash
python3 -m unittest discover model/training
```

Run model governance checks:

```bash
bash scripts/check-model-governance.sh
```

Train and regenerate report/artifact:

```bash
python3 model/training/train_model.py
```

The Kotlin runtime loads `artifacts/url_risk_centroid_model.json` as a classpath
resource, so retraining updates the runtime model through the generated artifact
instead of manual centroid copy/paste.

Run the baseline report:

```bash
python3 model/training/train_baseline.py
```

Rebuild the official-domain allowlist:

```bash
python3 model/training/build_official_domains.py --tranco /path/to/top-1m.csv
```

The Tranco input file is intentionally not committed.

## Data Handling Rules

- Commit sanitized fixtures only.
- Do not commit raw browsing logs, private URLs, or page bodies.
- Redact identifiers such as session tokens and account IDs.
- Keep large third-party raw datasets outside the repository.
- Preserve label provenance when rows are generated from LLM consensus rather
  than full human review.

For allowed claims, forbidden claims, and dataset governance rules, see the
"Data Governance" section of the [Model Card](evaluation/model-card.md).

## Related Documents

- [Project Report](../docs/project-report.md)
- [Model Evolution](docs/model-evolution.md)
- [Feature Specification](docs/feature-spec.md)
- [Dataset Schema](docs/dataset-schema.md)
- [Model Card](evaluation/model-card.md)
- [Trained Model Report](evaluation/trained-model-report.md)
