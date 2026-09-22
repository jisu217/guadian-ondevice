# Model Workspace

`model/` is the workspace for building a lightweight URL risk classifier that will later run inside the Android module.

The Android module owns runtime inference. This workspace owns model preparation, training, evaluation, and export.

```text
model/
  docs/        Dataset and feature design documents
  data/        Dataset notes and local dataset placeholders
  training/    Training scripts and experiment code
  evaluation/  Evaluation scripts, metrics, and test cases
  export/      Conversion scripts for mobile runtime formats
  artifacts/   Generated model artifacts such as prototype JSON models or mobile exports
```

Expected flow:

```text
Prepare URL dataset
  -> train lightweight URL risk classifier
  -> evaluate precision, recall, latency, and model size
  -> export mobile-ready artifact
  -> copy selected artifact into android/app/src/main/assets/
  -> connect it from OnDeviceModelJudge
```

`artifacts/` should contain only generated model outputs that are safe to commit. The current artifact is a small prototype classifier trained from URL features, not a fine-tuned LLM. Large datasets, raw logs, API keys, and private samples should stay outside the repository.

Start with [`docs/dataset-schema.md`](./docs/dataset-schema.md) and [`docs/data-collection-plan.md`](./docs/data-collection-plan.md) before collecting or generating samples. [`data/sample_urls.csv`](./data/sample_urls.csv) is a mixed seed fixture for pipeline tests, not a production training dataset. Then use [`docs/feature-spec.md`](./docs/feature-spec.md) to keep Python training and Android runtime feature extraction aligned.

Run model workspace tests with:

```bash
python3 -m unittest discover model/training
```

Run the baseline evaluator with:

```bash
python3 model/training/train_baseline.py
```

Run the first trained model experiment with:

```bash
python3 model/training/train_model.py
```

Current model direction:

```text
URL dataset
  -> deterministic URL features
  -> lightweight classifier
  -> Android on-device inference
```

This workspace does not assume Qwen, LLM fine-tuning, or a remote model runtime.
