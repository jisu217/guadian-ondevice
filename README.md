# GUADIAN On-device

![Status](https://img.shields.io/badge/status-in_progress-111111)

「GUADIAN」 is a service developed in the SKT FLY AI bootcamp to reduce harm
from malicious ad landing pages. This repository focuses on the judgment stage
of its detect -> judge -> intervene flow, and rebuilds the original
server-based judgment structure as an Android on-device pipeline.

This is not a single-model demo. The app combines cheap deterministic exits,
local evidence, rendered-page observation, and a small fine-tuned LLM whose
`HIGH` answers must be grounded in the prompt. The design goal is not to make
the model sound confident; it is to keep uncertainty visible until the pipeline
has enough evidence to act.

The current result is promising but unfinished. Run 3 of the on-device Qwen3
judge reaches 74.6% accuracy on a grouped-site test split, but still answers
`LOW` for 21 of 70 risky pages. The runtime works on an arm64 emulator; model
distribution, physical-device validation, and broader Shield coverage remain
future work.

## At a Glance

| Area | Current state |
| :-- | :-- |
| Runtime | Android app with manual URL judgment and browser-click Shield logging |
| Judgment strategy | cheap certain exits first, LLM last, uncertain results passed onward |
| Model | Qwen3-1.7B LoRA run 3, exported as Q4_K_M GGUF |
| Evaluation split | 118 cases, grouped by site so test sites are not seen in training |
| Current result | 74.6% accuracy, 0.73 macro F1, 56.5% `HIGH` recall |
| Main failure mode | missed risk: 21 of 70 risky pages answered `LOW` |
| Scope | research prototype, not a production security product |

Key documents:

- [Architecture](docs/architecture.md) explains the runtime pipeline and module boundaries.
- [Technical Report](docs/technical-report.md) summarizes design, evaluation, and removed approaches.
- [LLM Model Card](ml/docs/llm/model-card.md) states intended use, data, results, and limits.
- [Provenance](docs/provenance.md) records source, data, model, and license boundaries.
- [Original history](reference/original-guadian/docs/history.md) preserves the earlier GUADIAN project context.

## 1. Current Results

The on-device LLM (run 3, Q4_K_M GGUF) on the 118-case test split, grouped by
site so no test site was seen in training:

| Metric | Value |
| :-- | --: |
| Accuracy | 74.6% |
| Macro F1 | 0.73 |
| `HIGH` recall | 56.5% |
| Missed risk (`MEDIUM` or `HIGH` answered `LOW`) | 21 of 70 |
| Untuned base accuracy | 43.2% |

Fine-tuning taught the model the answer format and lifted accuracy by 31.4
points, but about one risky page in three is still answered `LOW`; that is the
next thing to fix. Source:
[LLM Evaluation Report](ml/reports/llm/evaluation-report.md).

Before the LLM, the rules and local knowledge settle part of the traffic
cheaply. On the 668-row URL dataset:

| Resolved by | Resolved | Correct |
| :-- | --: | --: |
| Deterministic rules | 135 | 98 |
| Local knowledge | 86 | 55 |
| **Total local** | **221** | **153** |

The other 447 rows go on to page inspection and the LLM. Source:
[Local Resolution Report](ml/reports/pipeline/local-resolution-report.md).
This table measures only the pre-LLM early exits, not the final pipeline
accuracy. These are evaluation-set numbers, not production estimates.

## 2. How It Works

Each layer returns a verdict when it is sufficiently certain. Otherwise, the
request moves to the next layer.

```text
Ad / link destination candidate
  ↓
Rule Engine
  ↓
Local Verdict Store
  ↓
Local RAG / local evidence   | RAG planned
  ↓
Page Content Inspection
  ↓
On-device LLM
  ↓
Verdict
```

The stages are:

| Stage | Role |
| :-- | :-- |
| Rule Engine | clear URL-level decisions, including a bundled phishing and malware blocklist |
| Local Verdict Store | reuses earlier judgments for the same normalized URL, after rules have run |
| Local RAG / local evidence | planned as retrieval; today keyword-matches local policy snippets |
| Page Content Inspection | loads the page in a hidden WebView and reads forms, text, links, and redirects |
| On-device LLM | reads the page observation and returns the final model verdict |

This flow corresponds to the judgment stage of 「GUADIAN」's
detect-judge-intervene structure. Its input is a destination candidate, provided
in the current implementation by manual URL entry (`JudgeActivity`) or a
supported browser link click (`ShieldAccessibilityService`). Browser clicks
skip page inspection and the LLM, because the browser is already loading the
page. See [Architecture](docs/architecture.md).

Each stage name above maps to one runtime class; file paths are under
`<module>/src/main/kotlin/com/guadian/ondevice/`:

| Stage | Class | Module | File |
| :-- | :-- | :-- | :-- |
| Rule Engine | `RuleBasedJudge` (`BlocklistRule`, `ApkRule`, `BrandImpersonationRule`, `SuspiciousUrlRule`) | `core/judge` | `rules/` |
| Local Verdict Store | `InMemoryVerdictStore` (run as `StoreJudge`) | `core/judge` | `store/VerdictStore.kt` |
| Local RAG / local evidence | `LocalKnowledgeJudge` (keyword retrieval today) | `core/judge` | `knowledge/LocalKnowledgeJudge.kt` |
| Page Content Inspection | `PageContentJudge` | `core/pagecontent` | `pagecontent/PageContentJudge.kt` |
| On-device LLM | `LlmJudge`, run by `LlamaCppRuntime` | `core/llm`, `core/llamacpp` | `llm/LlmJudge.kt` |
| Verdict | `Verdict`, assembled by `HybridJudge` | `core/judge` | `judge/` |

## 3. Current Scope

The pipeline runs on an arm64 emulator. The model file is not yet delivered
by the app, and physical-device validation, latency on real phones, and
broader Shield coverage remain future work.
