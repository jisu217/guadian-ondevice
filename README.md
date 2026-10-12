# GUADIAN On-device

![Status](https://img.shields.io/badge/status-in_progress-111111)

「GUADIAN」 is a service developed during the SKT FLY AI bootcamp to prevent
digital harm originating from malicious ads. This repository focuses on the
judgment stage of the detect -> judge -> intervene flow and rebuilds the
original server-based judgment architecture as an on-device pipeline.

It records that process: building the data, fine-tuning Qwen3-1.7B, comparing
training runs and quantized models, and connecting the selected model to the
Android app.

## Table of Contents

1. [Experiment Record](#1-experiment-record)
2. [Runtime Pipeline](#2-runtime-pipeline)
3. [Current Model](#3-current-model)
4. [Evaluation Summary](#4-evaluation-summary)
5. [Development History](#5-development-history)
6. [Repository Layout](#6-repository-layout)
7. [Limitations](#7-limitations)

## 1. Experiment Record

This repository is an experiment record for moving GUADIAN's judgment stage onto
the device. It covers the data-building process, Qwen3-1.7B fine-tuning,
training-run comparisons, quantized-model evaluation, and the work of connecting
the selected model to the Android app.

The resulting pipeline uses only local signals: URL facts, page facts, a compact
LLM's answers to six factual questions, and a verdict table shared by the app
and ML tooling.

## 2. Runtime Pipeline

The Android app evaluates URLs through a staged pipeline. Earlier stages collect
facts and can stop early when the verdict table has enough evidence; unresolved
cases proceed to the page inspector and, for the manual flow, the on-device LLM.

```text
URL
  -> Cache & URL
  -> Page inspection
  -> On-device LLM
  -> Verdict table
```

The runtime has two entry points:

| Entry point | Role |
| :-- | :-- |
| `JudgeActivity` | manual URL judgment during development and evaluation |
| `ShieldAccessibilityService` | browser-click detection through Android accessibility APIs |

No judgment stage sends the URL or page content to a server. The app stores
certain verdicts in a local in-memory cache keyed by normalized URL and verdict
table version.

## 3. Current Model

The current app-integrated model is run 6, a Qwen3-1.7B model fine-tuned with
LoRA and exported to GGUF for llama.cpp. It reads a compact Korean prompt built
from phone-observable landing-page evidence and returns one JSON line containing
answers to six factual questions.

The selected runtime model is Q8_0 (1.83 GB). The final risk is assigned outside
the model by the verdict table, not by model weights. This keeps risk policy
auditable and reduces the need to relabel and retrain the model when the policy
changes.

Q8_0 was selected over Q4_K_M because the 4-bit file lost more impersonation
evidence (`claims` recall 68.9% versus 80.0%). On an arm64 emulator, Q8_0 took
1.03-1.18 times as long as Q4_K_M for a verdict.

## 4. Evaluation Summary

Run 6 is the current runtime baseline. The figures below describe the model as
the app reads it after the reviewed-label pass described in the technical
report.

| Metric | Run 6 Q8_0 |
| :-- | --: |
| Test accuracy | 92.0% on 414 cases |
| Human-reviewed accuracy | 78.8% on 156 cases |
| Missed risk | 24 of 188 risky cases |
| OpenPhish pages answered `LOW` | 11 of 43 opened pages |
| Top-site false alarms | 2 of 62 opened Tranco sites |
| Teacher-answer exact match | 92.3% |
| `claims` recall | 80.0% |

The evaluation split is grouped by site, so no test site appears in training.
The expanded dataset combines the original GUADIAN landing URLs, OpenPhish,
Tranco Korean sites, and Naver search results.

## 5. Development History

Six training runs shaped the adopted design. The most important change was the
move from direct risk classification to factual model answers plus a shared
verdict table.

| Run | Change | Result | Decision |
| :-- | :-- | :-- | :-- |
| 1 | QLoRA on a 4-bit base, merged into fp16 for GGUF export | Accuracy fell after merge/export | Train against the fp16 base used for export |
| 2 | LoRA on the fp16 base | Output format stabilized, but Q4_K_M agreed with f16 on only 65.3% of cases | Improve quantization robustness |
| 3 | 5 epochs, effective batch 8, repeated `HIGH` cases | Q4_K_M reached 74.6% accuracy on the 118-case split | First app-integrated model |
| 4 | Expanded 2,094-case dataset, repeated `HIGH` cases 3x | Missed risk fell sharply, false alarms increased | Not adopted |
| 5 | Run 4 settings with aligned training labels | False alarms improved, missed risk rose again | Not adopted |
| 6 | Six-question model plus verdict table | 78.8% on human-reviewed cases, 24 of 188 risky pages missed | Current app-integrated model |

Runs 4 and 5 showed why aggregate accuracy was not sufficient. Missed risk,
false alarms on ordinary pages, quantization stability, and label alignment had
to be measured separately. Run 6 resolved the largest design issue by moving
risk criteria out of model weights and into an auditable table shared by the app
and training code.

## 6. Repository Layout

`app/` contains the Android application, UI, Shield accessibility service, and
app wiring. The runtime logic is split across `core/judge/` for the verdict
contract and pipeline, `core/pagecontent/` for hidden WebView inspection,
`core/llm/` for prompt and answer handling, and `core/llamacpp/` for the
Android llama.cpp integration.

`ml/` contains the Python ML workspace, datasets, training notebook, runtime
files, and generated reports. `docs/` holds the architecture, verdict design,
provenance, and technical report. `reference/` keeps the original GUADIAN
reference material.

## 7. Limitations

The current system is still a research and development baseline. Model timing
and memory have not yet been measured on a physical phone, and 11 of 43 opened
OpenPhish pages are still answered `LOW`. The final label review focused on run
6 disagreements, which favors run 6 in comparisons with earlier runs, and every
human-reviewed label was reviewed by the project owner.

Shield currently covers browser link clicks through accessibility APIs.
Arbitrary in-app WebViews, native ad surfaces, model packaging, device
performance, and broader real-world validation remain open.

## More

- [Technical Report](docs/technical-report.md): the full development record
- [LLM Evaluation Report](ml/reports/llm/evaluation-report.md): every score, generated from committed records
- [Model Card](ml/docs/llm/model-card.md) and [LLM Dataset](ml/docs/llm/dataset.md)
- [Verdict Design](docs/design.md) and [Architecture](docs/architecture.md): the verdict table and the Android runtime
