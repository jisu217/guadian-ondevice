# GUADIAN On-device

![Status](https://img.shields.io/badge/status-in_progress-111111)

GUADIAN On-device is an Android research prototype for judging the risk of ad
landing pages locally on the phone. It rebuilds the judgment stage of the
original GUADIAN service from a server-side LLM call into an on-device pipeline
using rendered-page inspection, deterministic policy rules, and a fine-tuned
Qwen3-1.7B model running through llama.cpp.

The repository contains both the Android runtime (`app/`, `core/`) and the ML
workspace (`ml/`) used to build datasets, fine-tune the model, export GGUF
files, and evaluate runtime behavior.

## Current Model

The current app-integrated model is run 6 Q8_0:

| Item | Value |
| :-- | :-- |
| Base model | Qwen3-1.7B |
| Fine-tuning | LoRA on fp16 base weights |
| Runtime format | GGUF, llama.cpp |
| Selected quantization | Q8_0 |
| File size | 1.83 GB |
| Model role | Answers six page-understanding questions |
| Final decision | Deterministic verdict table |

Earlier runs asked the model to output the final risk directly. Run 6 separates
page understanding from policy: the model reports structured observations, and
`ml/runtime/verdict_table.json` assigns the risk. This keeps judgment criteria
auditable and reduces the need to retrain the model for policy changes.

## Judgment Pipeline

```text
URL
  -> Cache & URL      stored verdict, blocklist, host imitation, APK URL
  -> Page             hidden WebView inspection and page facts
  -> On-device LLM    six factual answers for unresolved cases
  -> Verdict          answer validation and verdict table
```

The pipeline exits early when the verdict table can make a final decision from
URL or page facts. The LLM runs only when earlier stages cannot settle the case.
No judgment request is sent to a remote model server.

## Evaluation Snapshot

Run 6 Q8_0 is the current prototype baseline.

| Metric | Result |
| :-- | --: |
| Test accuracy | 92.0% on 414 cases |
| Human-reviewed accuracy | 78.8% on 156 cases |
| Missed risk | 24 of 188 `MEDIUM`/`HIGH` cases |
| OpenPhish pages answered `LOW` | 11 of 43 opened pages |
| Top-site false alarms | 2 of 62 opened Tranco sites |
| Unparseable base-model replies | 414 of 414 |

The model is not claimed to be a production phishing detector. It is a research
prototype with known coverage gaps, especially opened phishing pages whose risk
depends on signals not yet represented in the verdict table.

## Repository Guide

| Path | Purpose |
| :-- | :-- |
| `app/` | Android application and user-facing entry points |
| `core/` | Kotlin judgment pipeline, page inspection, LLM contract, llama.cpp bridge |
| `ml/` | Python package for data preparation, labeling, training, runtime files, and evaluation |
| `docs/technical-report.md` | Full development report, including failed runs and design changes |
| `docs/architecture.md` | Android runtime architecture and component contracts |
| `docs/design.md` | Verdict-table design and policy rationale |
| `ml/docs/llm/model-card.md` | Model card for the current on-device LLM |
| `ml/docs/llm/dataset.md` | Dataset construction, labeling, splits, and review process |
| `ml/reports/llm/evaluation-report.md` | Generated evaluation report from committed records |

## Verification

The runtime and ML contract are covered by Kotlin and Python tests:

```bash
./gradlew test
./gradlew lint
python -m unittest discover -s ml/tests -t ml
```

Instrumented Android tests run separately through `instrumented.yml` or
`./gradlew connectedDebugAndroidTest`.

## Status

The app runs on an arm64 emulator and can load the run 6 Q8_0 model for manual
URL judgment. Physical-device latency, memory behavior, broader Shield
validation, and distribution packaging remain open work.
