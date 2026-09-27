# Model Workspace

This directory contains the URL-risk model's dataset, training scripts,
generated artifact, and evaluation reports.

The model is a compact nearest-centroid URL-feature classifier used as one layer
(`OnDeviceModelJudge`) of the Android pipeline. It does not fetch page content
and is not a fine-tuned LLM.

## Table of Contents

1. [Layout](#1-layout)
2. [Dataset](#2-dataset)
3. [Results](#3-results)
4. [Commands](#4-commands)
5. [Runtime Integration](#5-runtime-integration)
6. [Related Documents](#6-related-documents)

## 1. Layout

| Path | Content |
| :-- | :-- |
| `data/sample_urls.csv` | sanitized training/evaluation samples |
| `data/fixtures/feature_parity_cases.csv` | Python/Kotlin feature parity fixture |
| `data/fixtures/rule_parity_cases.csv` | expected categories for the Kotlin URL rules |
| `data/fixtures/blocklist_filter_cases.csv` | Python/Kotlin blocklist filter parity fixture |
| `data/fixtures/llm_prompt_cases.jsonl` | Python/Kotlin on-device LLM prompt parity fixture (synthetic pages) |
| `data/fixtures/llm_page_signal_cases.json` | Python/Kotlin parity fixture for form, action, and text rules of page observation |
| `training/url_features.py` | Python feature extraction |
| `training/train_model.py` | nearest-centroid training, CV, threshold calibration, report |
| `training/train_baseline.py` | rule-scoring baseline |
| `training/build_official_domains.py` | allowlist builder |
| `training/build_blocklist_filter.py` | blocklist Bloom filter builder |
| `training/test_*.py` | unit tests |
| `runtime/url_risk_centroid_model.json` | generated model parameters (with `version`) |
| `runtime/official_domains.txt` | shared official-domain allowlist |
| `runtime/brand_domains.tsv` | brand -> official domains, shared by app rules and allowlist builder |
| `runtime/public_suffix_list.dat` | Mozilla Public Suffix List for registrable-domain lookup |
| `runtime/llm_brands.json` | brand data for the on-device LLM prompt, exported by `llm/prompt_format.py` |
| `runtime/blocklist.bloom`, `runtime/blocklist.meta.json` | phishing/malware blocklist as a Bloom filter, with its build record |
| `evaluation/trained-model-report.md` | generated training report |
| `evaluation/baseline-report.md` | generated baseline report |
| `evaluation/local-resolution-report.md` | generated per-layer local resolution report (`LocalResolutionReportTest`) |
| `data/train-split.csv`, `data/test-split.csv` | generated holdout split |
| `evaluation/model-card.md` | intended use, metrics, and limits |
| `docs/model-spec.md` | dataset schema and feature contract |
| `docs/model-evolution.md` | experiment history |

## 2. Dataset

`data/sample_urls.csv` holds 668 rows from three sources:

| Source | Rows | Content |
| :-- | --: | :-- |
| `synthetic` | 301 | controlled samples for feature and pipeline validation |
| `reference-derived` | 275 | 248 real ad landing rows from `gold_251.jsonl` + 27 GUADIAN-policy-derived rows |
| `public-benign` | 92 | public official/normal URLs |

Only sanitized fixtures are committed, and every row keeps its provenance in
`source`, `reviewed_by`, and `created_at`. The full schema and data-safety rules
are in [Model Specification](docs/model-spec.md#1-dataset); source lineage is in
[Provenance](../docs/provenance.md).

## 3. Results

The latest committed run uses a deterministic stratified holdout and 5-fold
cross-validation on the training split:

| Metric | Value |
| :-- | --: |
| Holdout accuracy | 75.00% |
| Holdout macro F1 | 74.63% |
| Rule baseline accuracy (same split) | 64.39% |
| 5-fold CV accuracy | 80.44% |
| 5-fold CV macro F1 | 81.44% |

These are the `v8-prep` numbers. `v8-prep` removed `test` from the suspicious-TLD list:
66 of the 107 `HIGH` rows are synthetic `.test` hosts, so the feature had been
separating synthetic rows rather than risky ones. Pooled holdout accuracy fell
from 79.55% to 75.00% and `HIGH` F1 from 93.02% to 75.68%; on the 72
non-synthetic holdout rows accuracy is 66.67% (68.06% for `v7`). The report
now splits holdout metrics by `source` so this gap stays visible.

The model improves over the rule baseline, but URL-only inference is not
sufficient for every risk grade. `MEDIUM` and `UNKNOWN` remain
context-sensitive and should be interpreted as part of the full hybrid
pipeline. Full metrics are in
[Trained Model Report](evaluation/trained-model-report.md).

## 4. Commands

Run from the repository root:

```bash
python3 -m unittest discover model/training
python3 model/training/train_model.py
python3 model/training/train_baseline.py
python3 model/training/build_official_domains.py --tranco /path/to/top-1m.csv
```

`train_model.py` regenerates the model artifact, the trained-model report,
`evaluation/metrics.json`, and the train/test split files. `metrics.json` is
the single source of current numbers: `scripts/check_doc_metrics.py` (run in
CI through `check-model-requirements.sh`) fails if any metric table in the
README, this file, the model card, model evolution, or the technical report
disagrees with it, so after a retrain the failing rows show exactly what to
update. `train_baseline.py` regenerates
`evaluation/baseline-report.md`, and `build_official_domains.py` rebuilds
`runtime/official_domains.txt` from a Tranco list.

## 5. Runtime Integration

`core/judge/build.gradle.kts` adds only `model/runtime` to the judgment
engine's resources (packaged into the app through that module), so Python training and the Kotlin runtime read the same files
while training data and test fixtures under `model/data` stay out of the APK.
`OnDeviceModelJudge` loads `url_risk_centroid_model.json` as a classpath
resource and rejects it at load time if a label, threshold, or feature list is
inconsistent; both `url_features.py` and `DomainAllowlist` read
`official_domains.txt`. Retraining therefore updates the runtime model directly,
with no export step, and the app shows the artifact's `version`.

## 6. Related Documents

[Model Specification](docs/model-spec.md) defines the dataset schema and feature
contract, and [Model Evolution](docs/model-evolution.md) records the experiment
history and modeling decisions. [Model Card](evaluation/model-card.md) states
intended use, metrics, and limits, and
[Trained Model Report](evaluation/trained-model-report.md) is the generated
latest training report.
