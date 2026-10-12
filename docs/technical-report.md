# Technical Report

This report documents the engineering path behind GUADIAN On-device: the
runtime design, model-development iterations, evaluation results, known
limitations, and the earlier URL-model approach that was removed. Concise
runtime contracts are documented separately in [Architecture](architecture.md),
and source/data lineage is documented in [Provenance](provenance.md).

## 1. Objective

GUADIAN On-device evaluates the risk of an ad landing page entirely on the
phone. The original GUADIAN service used a server-side LLM judge; this project
replaces that dependency with an Android-local pipeline:

```text
URL
  -> Cache & URL
  -> Page inspection
  -> On-device LLM
  -> Verdict table
```

The goal is not to claim production-grade phishing detection. The goal is to
measure how far a phone-local judge can go when it combines deterministic URL
and page facts with a compact fine-tuned LLM.

## 2. Runtime Design

The Android app has two judgment entry points:

| Entry point | Role |
| :-- | :-- |
| `JudgeActivity` | Manual URL judgment during development and evaluation |
| `ShieldAccessibilityService` | Browser-click detection through Android accessibility APIs |

The pipeline is implemented in Kotlin under `core/`. It collects facts in
stages and delegates the final risk decision to one table:

| Stage | Responsibility |
| :-- | :-- |
| Cache & URL | Reuse stored verdicts, check blocklist, host imitation, and APK URLs |
| Page | Load the destination in a hidden WebView and extract visible page facts |
| On-device LLM | Answer six factual questions for unresolved cases |
| Verdict | Validate model answers and map facts to risk through `verdict_table.json` |

The key design decision is that stages before the verdict table do not assign
independent risk grades. They only provide evidence. This makes policy changes
auditable and avoids distributing judgment criteria across rules, model
weights, and post-processing code.

## 3. Model Task

The current model is Qwen3-1.7B fine-tuned with LoRA and exported as GGUF for
llama.cpp. It reads a compact Korean prompt built from phone-observable page
facts:

- URL and redirect information
- title and visible text
- visible form fields
- APK links
- brand-imitation findings
- page-open status

Runs 1 to 5 trained the model to output a final `risk`, `type`, and evidence.
Run 6 changed the task: the model answers six factual questions, and the
verdict table assigns the final risk. This became the adopted design because
policy updates no longer require retraining the model.

## 4. Data

The first dataset had 551 cases:

| Source | Cases |
| :-- | --: |
| Original GUADIAN ad landing URLs | 251 |
| OpenPhish URLs | 300 |

The split was grouped by site so that test sites do not appear in training:

| Split | Cases |
| :-- | --: |
| Train | 381 |
| Validation | 52 |
| Test | 118 |

The 2026-10 dataset expanded coverage to OpenPhish, Tranco Korean sites, and
Naver search results. After excluding 38 bot-defense pages, the final dataset
used for the current question-model path contains 2,056 cases:

| Split | Cases | LOW | MEDIUM | HIGH |
| :-- | --: | --: | --: | --: |
| Train | 1,434 | 823 | 518 | 93 |
| Validation | 208 | 102 | 90 | 16 |
| Test | 414 | 226 | 153 | 35 |

Every test case was reviewed against the model-input evidence. Of the 414 test
cases, 156 were human-reviewed and 258 were reviewed by a Claude model. The data
process is specified in [LLM Dataset](../ml/docs/llm/dataset.md).

## 5. Development History

| Run | Change | Result | Decision |
| :-- | :-- | :-- | :-- |
| 1 | QLoRA on a 4-bit base, then merge to fp16 for GGUF export | Accuracy dropped after merge/export | Use LoRA on the fp16 base used for export |
| 2 | LoRA on fp16, 3 epochs, effective batch size 16 | Output format stabilized, but quantized models diverged from f16 | Improve training stability and quantization robustness |
| 3 | 5 epochs, effective batch size 8, repeated `HIGH` cases | Q4_K_M reached 74.6% on the 118-case split | Adopted as the first app-integrated model |
| 4 | Expanded 2026-10 dataset, repeated `HIGH` cases 3x | Missed risk fell sharply, but false alarms increased | Not adopted because reviewed-case quality regressed |
| 5 | Run 4 settings with aligned training labels | False alarms improved, missed risk rose again | Not adopted |
| 6 | Six-question model plus verdict table | Best reviewed-case result and cleaner policy boundary | Adopted as the current app-integrated model |

The main engineering lesson was that aggregate accuracy was not sufficient.
Missed risk, false alarms on ordinary pages, quantization stability, and label
alignment all had to be measured separately.

## 6. Evaluation

### 6.1 Earlier Direct-Risk Model

Run 3 Q4_K_M was the first model connected to the app:

| Metric | Value |
| :-- | --: |
| Accuracy | 74.6% on 118 cases |
| Macro F1 | 0.73 |
| `HIGH` recall | 56.5% |
| Missed risk | 21 of 70 risky cases |
| Unparseable replies | 0 |

Run 3 proved that the app could run a fine-tuned GGUF model locally, but it
still answered `LOW` for too many risky pages. It also embedded the risk policy
inside model weights, which made policy changes expensive.

### 6.2 Expanded Dataset Runs

Run 4 reduced missed risk on the 420-case split from 65 cases to 20 cases
against run 3, but warned on more ordinary pages and scored lower on the
human-reviewed subset. The failure mode came from label drift: some training
labels treated consultation pages as `personal_info` even when the first screen
did not show a personal-data field.

Run 5 aligned those labels, improving `LOW` recall but increasing missed risk.
It also learned an unintended association between brand mentions and
`personal_info`, caused by exceptions in the label rule.

### 6.3 Adopted Question Model

Run 6 changed the model output from direct risk classification to factual page
answers. The verdict table then converts those answers into a risk. The adopted
runtime model is run 6 Q8_0.

| Metric | Run 6 Q8_0 |
| :-- | --: |
| Test accuracy | 92.0% on 414 cases |
| Human-reviewed accuracy | 78.8% on 156 cases |
| Missed risk | 24 of 188 risky cases |
| OpenPhish pages answered `LOW` | 11 of 43 opened pages |
| Top-site false alarms | 2 of 62 opened Tranco sites |
| Teacher-answer exact match | 92.3% |
| `claims` recall | 80.0% |

Run 6 cleared the adoption bar only after a label review. As the app reads it,
its Q4_K_M file first missed 34 risky pages, over the bar of 25. Of those, 21 had
labels a Claude model wrote; the project owner relabeled them, and the 2 false
alarms with such labels, from the model input with the old label hidden, and
disagreed with 11 of the 23. The figures above use the reviewed labels. The
review covered only run 6's disagreements, which favors run 6 in comparisons
with earlier runs.

Q4_K_M reached the same overall accuracy but lost more brand-claim answers
(`claims` recall 68.9% versus 80.0%). Q8_0 was selected as the current runtime
baseline because the larger file preserved more impersonation evidence.

Full generated results are in
[LLM Evaluation Report](../ml/reports/llm/evaluation-report.md).

## 7. Why The Verdict Design Changed

The direct-risk model made three problems difficult to control:

1. Risk criteria lived inside model weights.
2. App rules and training labels could drift independently.
3. Each criteria change required relabeling and retraining.

The adopted design separates responsibilities:

| Layer | Owns |
| :-- | :-- |
| Code | Observable URL and page facts |
| LLM | Ambiguous page-understanding questions |
| Verdict table | Final policy decision |

This makes the model easier to evaluate and makes policy changes cheaper. The
design is documented in [Verdict Design](design.md).

## 8. Earlier Approach: URL Model

Before the on-device LLM was connected, the pipeline included a nearest-centroid
URL classifier over 22 features, including URL length, character ratios, risk
tokens, official-domain matches, suspicious TLDs, and tracking parameters. It
was trained in Python and ported to Kotlin with parity fixtures.

The URL model was removed for three reasons:

1. It resolved too few cases before later stages.
2. Its headline metrics depended heavily on synthetic examples.
3. The LLM and page inspector already had richer context for the same URLs.

The most useful lesson was methodological: synthetic artifacts can leak into
features. A `.test` TLD feature inflated `HIGH` performance because many
synthetic risky examples used reserved `.test` hosts. Removing that signal
reduced synthetic accuracy and exposed weaker real-world performance.

## 9. Verification

| Check | Scope |
| :-- | :-- |
| Kotlin unit tests | Verdict table, pipeline, URL/page facts, store, prompt and reply parity |
| Android lint | App sources and resources |
| Robolectric tests | Accessibility-node helper logic |
| Python tests | Prompt contract, labeling policy, runtime-file builders, evaluation reports |
| Requirement checks | Model artifact and runtime requirements |
| Instrumented tests | `JudgeActivityTest`, `PageInspectorTest` |

Run 6 Q8_0 has been exercised on an arm64 emulator. In emulator measurements on
2026-10-11, Q8_0 verdict generation took about 1.03-1.18x as long as Q4_K_M
across the same pages. These measurements do not represent physical-device
latency.

## 10. Limitations

- Physical-device latency and memory behavior have not been measured.
- The model file is not distributed because the training-data terms constrain
  redistribution.
- Run 6 Q8_0 still answers `LOW` for 24 of 188 risky test cases.
- It answers `LOW` for 11 of 43 opened OpenPhish pages.
- Most training labels come from a teacher model, and many reviewed test labels
  were reviewed by a Claude model rather than by a person.
- Shield detects candidate browser clicks; it does not prove the click came
  from an ad.
- Shield currently skips the LLM stage because of interaction-time constraints.
- In-app WebViews and native ad surfaces are not covered.

## 11. Next Steps

1. Measure Q8_0 latency and memory on a physical Android device.
2. Improve verdict-table coverage for opened phishing pages.
3. Validate Shield behavior from browser click to logged verdict.
4. Add a model packaging path that respects dataset and model distribution
   constraints.
5. Periodically refresh the blocklist and re-run regression evaluation.
