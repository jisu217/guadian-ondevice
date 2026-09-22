# Model Workspace

`model/` is the workspace for building the URL risk model that will later run inside the Android module.

The Android module owns runtime inference. This workspace owns model preparation, training, evaluation, and export.

```text
model/
  docs/        Dataset and feature design documents
  data/        Dataset notes and local dataset placeholders
  training/    Training scripts and experiment code
  evaluation/  Evaluation scripts, metrics, and test cases
  export/      Conversion scripts for mobile runtime formats
  artifacts/   Exported model files such as url_risk.tflite
```

Expected flow:

```text
Prepare URL dataset
  -> train baseline risk classifier
  -> evaluate precision, recall, latency, and model size
  -> export mobile model
  -> copy exported artifact into android/app/src/main/assets/
  -> connect it from OnDeviceModelJudge
```

`artifacts/` should contain only generated model outputs that are safe to commit. Large datasets, raw logs, API keys, and private samples should stay outside the repository.

Start with [`docs/dataset-schema.md`](./docs/dataset-schema.md) and [`docs/data-collection-plan.md`](./docs/data-collection-plan.md) before collecting or generating samples. [`data/sample_urls.csv`](./data/sample_urls.csv) is a mixed seed fixture for pipeline tests, not a production training dataset. Then use [`docs/feature-spec.md`](./docs/feature-spec.md) to keep Python training and Android runtime feature extraction aligned.

Run model workspace tests with:

```bash
python3 -m unittest discover model/training
```

Run the baseline evaluator with:

```bash
python3 model/training/train_baseline.py
```
