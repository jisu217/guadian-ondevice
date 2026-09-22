# Model Workspace

`model/` is the workspace for building the URL risk model that will later run inside the Android module.

The Android module owns runtime inference. This workspace owns model preparation, training, evaluation, and export.

```text
model/
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
