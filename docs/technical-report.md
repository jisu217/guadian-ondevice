# Technical Report

This report summarizes the problem, runtime design, on-device model, and
evaluation of GUADIAN On-device, and records the URL model that was tried and
removed. Runtime details are in [Architecture](architecture.md); source lineage
is in [Provenance](provenance.md).

## Table of Contents

1. [Overview](#1-overview)
2. [Problem](#2-problem)
3. [Reference Basis](#3-reference-basis)
4. [Runtime](#4-runtime)
5. [On-device LLM](#5-on-device-llm)
6. [Evaluation](#6-evaluation)
7. [Earlier Approach: URL Model](#7-earlier-approach-url-model)
8. [Verification](#8-verification)
9. [Limitations](#9-limitations)
10. [Next Steps](#10-next-steps)

## 1. Overview

GUADIAN On-device is an Android prototype that judges the risk of an ad's
destination entirely on the phone. It replaces the original GUADIAN server
judge (`claude-haiku-4-5`) with deterministic rules, a local verdict store,
local policy knowledge, rendered-page inspection, and a fine-tuned Qwen3-1.7B
run with llama.cpp. The Android code is in `app/` and `core/`; the Python
package that builds the model's data, trains, and evaluates it is in `ml/`.

The app has two entry points: `JudgeActivity` for manual URL input, and
`ShieldAccessibilityService`, which detects link clicks in supported browsers
through Android accessibility APIs. The app builds and runs on an arm64
emulator; physical-device validation is pending.

## 2. Problem

Ad landing pages can lead to phishing, APK downloads, brand impersonation,
credential capture, or payment lures. A server judge can read the page with a
large model, but every visit then leaves the phone, and the service pays for
latency and API cost. This project asks how much of that judgment a phone can
do by itself.

## 3. Reference Basis

The project was developed with reference to the earlier GUADIAN project and a
sibling model-comparison dataset. Preserved source material is stored under
`reference/`; copied, derived, and reference-only boundaries are documented in
[Provenance](provenance.md).

The repository is a new implementation, not a direct port. The new work covers
the local Kotlin judgment pipeline, the page-content inspection, the
accessibility-triggered Shield flow, the LLM dataset and fine-tuning pipeline,
the on-device llama.cpp runtime, and the parity tests that hold the Kotlin
prompt and reply reading to the Python reference.

## 4. Runtime

```text
URL
  -> RuleBasedJudge
  -> InMemoryVerdictStore (StoreJudge)
  -> LocalKnowledgeJudge
  -> PageContentJudge        (manual entry only; skipped for Shield clicks)
  -> LlmJudge                (manual entry only)
  -> Verdict
```

Cheap deterministic checks run first, and the rules come before the store so
that a cached verdict can never skip them. Page inspection and the LLM run
only when nothing earlier is certain. Control flow and the `Verdict` contract
are described in [Architecture](architecture.md).

## 5. On-device LLM

The LLM reads a short Korean prompt built from what page inspection saw
(address, title, visible text, form fields, APK links, redirects, and host
imitation findings) and answers with one JSON line: risk, type, and, for
`HIGH`, the prompt line that supports it. The app accepts a `HIGH` only when
that line appears in its own prompt, and accepts a `personal_info` warning only
when the page shows a personal field or a form worded to collect contact
details, the rule the labeling guide sets for the label.

The 551 training and evaluation cases are the original team's 251 gold ad URLs
plus 300 OpenPhish URLs, rendered in a headless mobile browser and labeled by
the original server's model and criteria, then corrected by a written policy
and by hand. Run 3 trains a LoRA adapter on the fp16 base, merges it, and
ships a Q4_K_M GGUF (1.11 GB). The 2026-10 dataset grows this to 2,094 cases
with OpenPhish, Tranco, and Naver search URLs; runs 4 and 5 trained on it.
Its labels now follow one labeling guide: the teacher relabeled every case from
the on-device model's own prompt, and 38 pages that a bot defense served
instead of the site are left out, leaving 2,056 cases. All 414 test cases are
reviewed, 133 by a person and the rest by a Claude model. The data process is in
[LLM Dataset](../ml/docs/llm/dataset.md), and intended use and limits in the
[LLM Model Card](../ml/docs/llm/model-card.md).

## 6. Evaluation

On the 118-case test split, grouped by site so no test site was seen in
training, the shipped run 3 Q4_K_M model scores:

| Metric | Value |
| :-- | --: |
| Accuracy | 74.6% |
| Macro F1 | 0.73 |
| `HIGH` recall | 56.5% |
| Missed risk (`MEDIUM` or `HIGH` answered `LOW`) | 21 of 70 |
| Untuned base accuracy | 43.2% |

Every GGUF variant and the Colab runs are compared in the
[LLM Evaluation Report](../ml/reports/llm/evaluation-report.md), generated
from the committed records. Missed risk is the main weakness: run 3 raised
accuracy by 7.7 points over run 2 without reducing it.

Run 4, trained on the 2026-10 dataset, answers `LOW` for 20 of 208 risky test
pages against 65 for run 3 on the same 420 cases (both Q4_K_M), but warns on
more ordinary pages; on the 133 human-reviewed cases it scores 67.7% against run
3's 71.4%. The extra warnings were `personal_info` answers on pages without a
personal field, which the teacher labels in training often called `MEDIUM`. The
app's new check and matching training labels for run 5 followed; with the check,
run 4 reaches 74.4% on the human-reviewed cases, a figure the check was tuned
on. Run 4 is not shipped.

Run 5 keeps run 4's settings and changes only those training labels. It raises
`LOW` recall but misses more risky pages (27 against 20) and finds fewer `HIGH`
ones, and as the app reads it scores 70.7% on the human-reviewed cases, below
run 3's 72.2%. The label rule keeps `personal_info` when the prompt has a brand
mention line, so after the change the model learned to answer `personal_info`
on pages that only mention a brand. Run 5 is not shipped either.

The original server judge (`claude-haiku-4-5`, the teacher) can be scored on the
same split from its labels. It misses the fewest risky pages (12) but warns on
45 of the 76 ordinary pages among the human-reviewed cases, where it scores
51.1%. Those labels apply the rules added since the original criteria, so the
gap measures a difference in criteria as much as in judgment.

The criteria were then rewritten as one labeling guide, built on the original
service's grading principle that `HIGH` needs certainty, severity, and a
mismatched identity, and the teacher relabeled the dataset with it. On the
human-reviewed cases it now agrees with people on 83.5% (111 of 133). The 30
disagreements were settled case by case and 11 human labels changed, so earlier
scores are restated on the relabeled split: as the app reads them, run 3 scores
72.2% on the human-reviewed cases with 59 missed risky pages, and run 4 77.4%
with 22. Run 4's figure still rests on an app check designed after seeing this
split.

Because the guide and the reviewed labels come from the same people, the report
also scores what needs no label: confirmed phishing URLs from OpenPhish answered
`LOW`, and top Tranco sites warned on. On the 43 phishing pages that still
opened, run 3 answers `LOW` on 12 and run 4 on 4; on the 62 top sites that
opened, both warn on 4 or 5. These agree with the labeled scores.

The layers before the LLM are measured on the 668-row URL dataset
(`ml/data/url/sample_urls.csv`): the rules resolve 135 rows (98 correct) and
local knowledge 86 (55 correct), leaving 447 for page inspection and the LLM
([Local Resolution Report](../ml/reports/pipeline/local-resolution-report.md)).

## 7. Earlier Approach: URL Model

Before the LLM, the pipeline had a third layer: a nearest-centroid classifier
over 22 URL features (lengths, character ratios, risk tokens, official-domain
match, suspicious TLD, and tracking and redirect parameters), trained in
Python and run in Kotlin with a parity fixture between them. It was removed
once the LLM ran on the device.

It was removed for three reasons. It settled few cases: on its 132-row holdout
it resolved 9 URLs before later layers, 7 of them correctly. Its numbers
leaned on synthetic data: on the 72 non-synthetic holdout rows its accuracy
was 66.67%. And the LLM already reads the address and the host-imitation
findings, so the URL model mostly repeated what a later layer sees with more
context, while its training code, fixtures, reports, and checks had to be
maintained alongside. Nothing in its code moved to other layers: its
tracking-parameter list was already covered by the store's `UrlKey`, and its
nested-URL, short-link, and suspicious-TLD signals are too common on benign ad
URLs to settle a verdict alone. They are candidates for LLM prompt lines in a
later training run. The code and its reports remain in the repository history.

The version history below is kept because its lessons carry over to the LLM:
realistic data lowers headline numbers, and synthetic artifacts leak into
features.

**v1, rule-scoring baseline.** Hand-written feature scores (APK, login, brand,
event, free, shortener, and suspicious-TLD signals) summed into a risk label.
It was easy to explain but too coarse for mixed ad URLs, reaching 68.41%
accuracy on the full 668-row dataset and 68.94% on the final holdout split
(62.57% and 64.39% after the v8-prep feature change below).

**v2, synthetic-only centroid.** A nearest-centroid classifier trained on
balanced synthetic and policy-derived samples reached 97.50% accuracy, but the
dataset was too controlled to represent real ad landing URLs. It was treated
as an over-optimistic checkpoint, not a result.

**v3, realistic data merge.** Adding 248 real ad landing rows from
`ml/data/raw/gold_251.jsonl`, then targeted banking, short-link, APK, and
Korean-domain edge cases, produced the 668-row dataset. Holdout accuracy fell
to 72.73% (macro F1 74.15%), exposing that `MEDIUM` and `UNKNOWN` often require
signals beyond URL text.

**v4–v5, confidence thresholds.** v4 used one distance threshold shared by all
labels, so a `LOW`/`MEDIUM` boundary case collapsed confidence for
well-separated `HIGH` cases. v5 gave each predicted label its own threshold,
calibrated by 5-fold cross-validation on the training split.

**v6–v7, feature changes.** v6 applied `log1p` to URL, host, path, and query
lengths before min-max scaling, so very long tracking URLs no longer dominated
the scale. v7 added query count, UTM and tracking parameter count, nested URL,
encoded URL, ad-click host, and base64-like token features. Both were retrained
together in `a95b51b`; an ablation run (2026-09-25) on the same dataset, split,
and training code separated their effects:

| Variant | Holdout acc. | Holdout macro F1 | `MEDIUM` recall | CV acc. | CV macro F1 |
| :-- | --: | --: | --: | --: | --: |
| v3–v5 (no `log1p`, no v7 features) | 72.73% | 74.15% | 50.00% | 73.16% | 74.95% |
| v6 only (`log1p`) | 79.55% | 80.98% | 70.45% | 79.69% | 81.18% |
| v7 only (v7 features) | 79.55% | 80.72% | 65.91% | 80.63% | 82.04% |
| v6 + v7 | 79.55% | 80.98% | 70.45% | 81.19% | 82.70% |

v6 accounted for the whole holdout gain; v7 on top left holdout accuracy
unchanged and raised 5-fold CV accuracy from 79.69% to 81.19%.

**v8-prep, `.test` leak removed (last version).** v7 treated the `.test` TLD as
suspicious, but the synthetic rows use the reserved `.test` TLD and 66 of the
107 `HIGH` rows were `.test` hosts, so the feature partly learned "synthetic"
instead of "risky". v8-prep removed it and retrained on the same data and
split:

| Variant | Holdout acc. | Holdout macro F1 | `HIGH` F1 | Synthetic acc. (60) | Non-synthetic acc. (72) | CV acc. | CV macro F1 |
| :-- | --: | --: | --: | --: | --: | --: | --: |
| v7 | 79.55% | 80.98% | 93.02% | 93.33% | 68.06% | 81.19% | 82.70% |
| v8-prep | 75.00% | 74.63% | 75.68% | 85.00% | 66.67% | 80.44% | 81.44% |

Most of the drop was on synthetic rows, where the leaked signal lived; on
non-synthetic rows the change was 1.39 points.

## 8. Verification

Most checks run in CI on every pull request; instrumented tests run in a
separate nightly or on-demand workflow:

| Type | Scope | Run by |
| :-- | :-- | :-- |
| Kotlin unit tests | judges, rules, store, page-inspection reading, LLM prompt, chat template, and reply parity | CI (`./gradlew test`) |
| Android lint | app sources and resources | CI (`./gradlew lint`) |
| Robolectric tests | accessibility-node helper logic | CI (`./gradlew test`) |
| Python tests | prompt contract, labeling policy, URL lists, notebook, runtime-file builders, evaluation report | CI |
| Requirement checks | `scripts/check-model-requirements.sh` | CI |
| Instrumented tests | `JudgeActivityTest`, `PageContentJudgeTest` | `instrumented.yml` or `./gradlew connectedDebugAndroidTest` |

On an arm64 emulator, the LLM stage has loaded the model and returned verdicts
in about 4–5 seconds with a 4-bit run 1 model; run 3 Q4_K_M has not been timed
on the device yet. Shield verdicts are written to Logcat (tag `GuadianShield`);
that output has not yet been verified by hand.

## 9. Limitations

- No physical-device validation yet.
- About one risky page in three is answered `LOW` by the LLM.
- The model file is not distributed (its training data's terms forbid it).
- Training labels come mostly from one teacher model, and most test labels were
  reviewed by a Claude model rather than a person.
- Shield detects candidate browser clicks; it does not prove a click is an ad,
  and it skips page inspection and the LLM.
- Arbitrary in-app WebViews and native ad surfaces are not covered.
- The project is a research prototype, not a production security product.

## 10. Next Steps

1. Train run 6 on the guide's labels, and ship it or run 4 if one clears the
   bar set before run 5: human-reviewed accuracy above run 3's as the app reads
   it (72.2% on the relabeled split), and at most 25 missed risky pages.
2. Time the shipped model on the emulator and on a physical device.
3. Update the bundled blocklist periodically.
4. Improve accessibility permission onboarding and the result UI.
