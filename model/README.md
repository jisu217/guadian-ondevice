# Model Workspace

This directory contains the local URL-risk model dataset, training scripts,
generated artifact, and evaluation reports.

The model is a compact URL-feature classifier used inside the broader Android
pipeline. It does not fetch page content and it is not a fine-tuned LLM.

## Layout

| Path | Purpose |
| :-- | :-- |
| `data/sample_urls.csv` | sanitized training/evaluation samples |
| `data/official_domains.txt` | shared official-domain allowlist |
| `data/feature_parity_cases.csv` | Python/Kotlin feature parity fixture |
| `docs/model-spec.md` | dataset schema and feature contract |
| `docs/model-evolution.md` | model experiment history |
| `training/` | feature extraction, baseline, training, and tests |
| `artifacts/url_risk_centroid_model.json` | generated model parameters |
| `evaluation/model-card.md` | intended use, metrics, and limits |
| `evaluation/trained-model-report.md` | generated trained-model report |

## Dataset

Current committed dataset:

| Source | Count |
| :-- | --: |
| Synthetic and policy-derived samples | 420 |
| Real ad landing references | 248 |
| Total | 668 |

Row-level provenance is recorded through `source`, `reviewed_by`, and
`created_at`. See [Provenance](../docs/provenance.md).

## Results

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy | 68.94% |
| Cross-validation accuracy | 81.19% |
| Cross-validation macro F1 | 82.70% |

`HIGH` is strongest; `MEDIUM` and `UNKNOWN` remain context-sensitive.

## Commands

```bash
python3 -m unittest discover model/training
python3 model/training/train_model.py
python3 model/training/train_baseline.py
python3 model/training/build_official_domains.py --tranco /path/to/top-1m.csv
```

Retraining regenerates the JSON artifact and evaluation report. Kotlin loads
the artifact directly as a classpath resource.

## Data Rules

- Commit sanitized fixtures only.
- Do not commit raw browsing logs, private URLs, or page bodies.
- Redact identifiers such as session tokens and account IDs.
- Preserve whether a label came from policy, manual review, human review, or
  LLM consensus.

Related documents:

- [Model Specification](docs/model-spec.md)
- [Model Evolution](docs/model-evolution.md)
- [Model Card](evaluation/model-card.md)
- [Trained Model Report](evaluation/trained-model-report.md)
