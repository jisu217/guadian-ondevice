# On-device LLM Judge Model Card

Qwen3-1.7B fine-tuned with LoRA to answer six questions about an ad landing
page from what a phone can observe on it. This card states what the model is
for, what it was trained and scored on, and what may not be claimed about it.
The numbers come from the
[LLM Evaluation Report](../../reports/llm/evaluation-report.md), which is
generated from committed records.

## Table of Contents

1. [Overview](#1-overview)
2. [Intended Use](#2-intended-use)
3. [Data](#3-data)
4. [Training](#4-training)
5. [Evaluation](#5-evaluation)
6. [Runtime Contract](#6-runtime-contract)
7. [Known Limits](#7-known-limits)
8. [Forbidden Claims](#8-forbidden-claims)

## 1. Overview

The model is the last stage of the app's judgment pipeline, `LlmJudge`
in `core/llm`, run with llama.cpp on the phone's CPU. It reads a short Korean
prompt (address, title, visible text, form fields, APK links, redirects, and
host-imitation findings) and answers the six questions of the
[Question Guide](question-guide.md) as one JSON line: `claims` (the
organization the page claims to be, or `null`) with the prompt line that shows
it as `evidence`, and yes/no answers for `sign_in`, `payment`, `investment`,
`reward_pressure`, and `content_farm`. The model does not grade the risk: the
verdict table (`ml/runtime/verdict_table.json`) turns its answers and the facts
code computes into the risk ([Verdict Design](../../../docs/design.md)). It
replaces the original GUADIAN server's call to `claude-haiku-4-5`; nothing
leaves the phone.

The current app-integrated model is run 6 Q8_0,
`guadian-qwen3-1.7b-run6-Q8_0.gguf` (1.83 GB). It replaced run 3, a model that
graded the risk itself; the app still reads that legacy reply format for
compatibility. The file is not bundled with the app and is not distributed (see
[Provenance](../../../docs/provenance.md)).

## 2. Intended Use

The model describes a page the user is about to visit from an ad, for a
prototype that logs verdicts. Only pages the verdict table cannot settle from
the earlier stages (stored verdicts, URL facts, page inspection) reach it. A
claim counts only when its evidence is one of the prompt's own lines,
verbatim; the app drops it otherwise.

It is not meant to judge arbitrary web content, to run without the page
observation, or to be the only defense against phishing.

## 3. Data

The 2026-10 dataset has 2,094 cases: the original team's 251 gold ad URLs,
OpenPhish feed URLs, Tranco Korean sites, and Naver search results. Each page
was rendered in a headless mobile browser with the app's page inspection
script. Claude Sonnet 5.5 (`claude-sonnet-5-5`) answered the six questions from
the on-device model's own prompt, and the verdict table turned the answers and
the page's facts into each case's risk; human review overrides that risk. 38
pages a bot defense served instead of the site are left out, leaving 2,056.
The full process is in [LLM Dataset](dataset.md).

Splits are grouped by site, so no test site appears in training. Risk counts
are the labels the model is scored against:

| Split | Cases | LOW | MEDIUM | HIGH |
| :-- | --: | --: | --: | --: |
| train | 1,434 | 823 | 518 | 93 |
| validation | 208 | 102 | 90 | 16 |
| test | 414 | 226 | 153 | 35 |

Every test case is reviewed against the criteria from the model's input: 156
by a person (23 of them relabeled blind on 2026-10-09, after run 6) and 258 by
a Claude model (Claude Opus 5.5). Page text holds third-party content and
personal data, so the dataset stays in the git-ignored `data/llm/`; only
synthetic fixtures are committed.

## 4. Training

Run 6 trains a LoRA adapter (rank 16, alpha 32, dropout 0.05, all attention
and MLP projections) on the fp16 base with run 4's settings: three epochs,
learning rate 2e-4 with cosine decay, effective batch size 8, loss on the
answer tokens only; the epoch with the lowest validation loss is kept. The
training target is the teacher's answers to the six questions. The adapter is
merged into the fp16 weights, converted to GGUF, and quantized.
`notebooks/train_llm.ipynb` holds the exact settings.

## 5. Evaluation

On the 414-case test split, as the app reads its answers, Q8_0 reaches 92.0%
accuracy and 78.8% on the 156 human-reviewed cases (run 3: 72.4%, run 4:
71.8%). It answers `LOW` for 24 of the 188 `MEDIUM` and `HIGH` cases (missed
risk), answers `LOW` on 11 of the 43 OpenPhish pages that opened, and warns on
2 of the 62 Tranco sites that opened. Against the teacher, all six answers
match on 92.3% of the test cases; `claims` recall is 80.0%. The untuned base
parses on none.

Q4_K_M scores the same accuracy but misses 25 risky pages and recalls 68.9% of
the teacher's claims, losing two impersonation pages of listed brands. On an
arm64 emulator a verdict took Q8_0 1.03-1.18 times as long as Q4_K_M
([Technical Report](../../../docs/technical-report.md)). Q8_0 is the selected
runtime baseline because it preserves more brand-claim evidence.

No test case has a yes answer for `payment` or `reward_pressure`, and the model
never answers yes to `content_farm`, so those three questions are not
measured. One human-reviewed case moves that accuracy by about 0.64 points.

## 6. Runtime Contract

The prompt the app builds (`PromptBuilder.kt`), the chat template
(`ChatFormat.kt`), and the reply reading (`AnswerReader.kt`) must match the
Python reference in `guadian_ml/llm/contract/`; parity tests replay the
cases in `fixtures/llm/`. Decoding is greedy with at most 192 new tokens and a
1,024-token context; run 6's longest test answer is 155 tokens. A reply that
cannot be read gives an uncertain `UNKNOWN`. The page is read by
`ml/runtime/page_inspection.js`, which the training-data collector runs as well.

## 7. Known Limits

Phishing that opened is the weakest point: 11 of 43 such pages are answered
`LOW`, mostly through gaps in the verdict table rather than the model (the
teacher's own answers miss 21 risky pages through the same table). The model
does not recognize content farms. Labels come from one teacher model, and most
test labels were reviewed by a Claude model rather than a person. Speed is
measured only on an arm64 emulator, as a ratio between files, not on a physical
device; Q8_0 needs about 1.8 GB of memory, which may not fit phones with 4 GB
of RAM.

## 8. Forbidden Claims

The following claims are not supported:

- The model is production-ready or suitable as a sole security control.
- A `LOW` answer proves a page is safe.
- The test set represents real user traffic.
- Teacher labels are verified incident labels.
- The model matches or exceeds the original service's Claude judge.
- The model has been validated on a physical device.
