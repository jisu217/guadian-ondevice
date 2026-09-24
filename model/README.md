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

The latest committed training run uses a deterministic holdout split and a
5-fold cross-validation pass. The nearest-centroid model improves over the
rule-scoring baseline, but the results still show that URL-only inference is
not enough for every risk grade.

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 79.55% |
| Holdout macro F1 | 80.98% |
| Baseline accuracy | 68.94% |
| Cross-validation accuracy | 81.19% |
| Cross-validation macro F1 | 82.70% |

`HIGH` is strongest on clear APK and impersonation-like patterns. `MEDIUM` and
`UNKNOWN` remain context-sensitive and should be interpreted as part of the full
hybrid pipeline.

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

The canonical dataset schema and data-safety rules live in
[Model Specification](docs/model-spec.md#dataset). In short, commit sanitized
fixtures only and preserve row-level provenance.

Related documents:

| Document | Purpose |
| :-- | :-- |
| [Model Specification](docs/model-spec.md) | dataset schema and feature contract |
| [Model Evolution](docs/model-evolution.md) | experiment history and modeling decisions |
| [Model Card](evaluation/model-card.md) | intended use, metrics, and limits |
| [Trained Model Report](evaluation/trained-model-report.md) | generated latest training report |
