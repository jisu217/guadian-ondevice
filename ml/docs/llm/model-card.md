# On-device LLM Judge Model Card

Qwen3-1.7B fine-tuned with LoRA to grade an ad landing page from what a phone
can observe on it. This card states what the model is for, what it was trained
and scored on, and what may not be claimed about it. The numbers come from the
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
host-imitation findings) and answers with one JSON line: `risk` (`LOW`,
`MEDIUM`, `HIGH`), `type`, and, for `HIGH`, the prompt line that supports it.
It replaces the original GUADIAN server's call to `claude-haiku-4-5`; nothing
leaves the phone.

The current model is run 3, shipped as `guadian-qwen3-1.7b-run3-Q4_K_M.gguf`
(1.11 GB). The file is not bundled with the app and is not distributed (see
[Provenance](../../../docs/provenance.md)).

## 2. Intended Use

The model grades a page the user is about to visit from an ad, for a
prototype that logs verdicts. Only pages that the earlier stages (rules,
stored verdicts, local knowledge, page content inspection) leave
uncertain reach it. A `HIGH` answer counts only when its type is
impersonation, credentials, or apk and its evidence is one of the prompt's own
lines; the app lowers it to `MEDIUM` otherwise. A `MEDIUM personal_info` answer
counts only when the page shows a personal field, or a form whose text names a
contact field or asks consent to collect personal information; the app lowers
it to `LOW` otherwise.

It is not meant to judge arbitrary web content, to run without the page
observation, or to be the only defense against phishing.

## 3. Data

The 551 cases come from two sources: the 251 ad landing URLs of the original
team's gold set (`data/raw/gold_251.jsonl`) and 300 URLs from the OpenPhish
community feed collected on 2026-09-26. Each page was rendered in a headless
mobile browser, labeled by `claude-haiku-4-5` with the original service's
criteria, and corrected by the labeling policy and by hand; the full process
is in [LLM Dataset](dataset.md).

Splits are grouped by registrable domain, so no test site appears in training,
and stratified by risk:

| Split | Cases | LOW | MEDIUM | HIGH |
| :-- | --: | --: | --: | --: |
| train | 381 | 95 | 228 | 58 |
| validation | 52 | 10 | 32 | 10 |
| test | 118 | 48 | 47 | 23 |

All 47 hand-reviewed cases are in the test split. Page text holds third-party
content and personal data, so the dataset stays in the git-ignored
`data/llm/`; only synthetic fixtures are committed.

## 4. Training

Run 3 trains a LoRA adapter (rank 16, alpha 32, dropout 0.05, all attention
and MLP projections) on the fp16 base for five epochs, learning rate 2e-4
with cosine decay, effective batch size 8, loss on the answer tokens only,
with `HIGH` training cases repeated twice; the epoch with the lowest
validation loss is kept. The adapter is merged into the fp16 weights,
converted to GGUF, and quantized to Q4_K_M. Plain LoRA replaced QLoRA after
run 1 lost about six points in the merge, and run 3 takes about four times
run 2's 72 optimizer steps. `notebooks/train_llm.ipynb` holds the exact settings.

## 5. Evaluation

On the 118-case test split, Q4_K_M reaches 74.6% accuracy, macro F1 0.73, and
56.5% `HIGH` recall. It answers `LOW` for 21 of the 70 `MEDIUM` and `HIGH`
cases (missed risk), 9 of them pages collecting personal details, and on the
47 hand-reviewed cases it reaches 68.1% and finds none of the `HIGH` ones. The
untuned base scores 43.2% and cannot follow the answer format (112 of 118
replies unparseable). Q4_K_M keeps the f16 file's answer on 91.5% of cases
and scores at least as well as the larger files, so it ships as the smallest.

These figures come from 118 cases, so one case moves accuracy by 0.85 points;
differences of a few points between runs are within noise.

## 6. Runtime Contract

The prompt the app builds (`PromptBuilder.kt`), the chat template
(`ChatFormat.kt`), and the reply reading (`AnswerReader.kt`) must match the
Python reference in `guadian_ml/llm/contract/`; parity tests replay the
cases in `fixtures/llm/`. Decoding is greedy with at most 96 new tokens and a
1,024-token context. A reply that cannot be read counts as `MEDIUM`. The page
is read by `ml/runtime/page_inspection.js`, which the training-data collector
runs as well.

## 7. Known Limits

Missed risk is the largest problem: about one risky page in three is answered
`LOW`. Answers can flip under small changes to numeric lines in the prompt.
Labels come mostly from one teacher model, corrected by policy and a limited
human review, so they carry that model's judgment. Speed is measured only on
an arm64 emulator so far (about 4–5 s per verdict with a 4-bit model), not on
a physical device. The app's prompt now names form fields and leaves out hidden
ones, a format run 3 was not trained on; scored on it, run 3 reaches 78.3% on
the 2026-10 test split (`ml/reports/llm/evaluation-report.md`).

## 8. Forbidden Claims

The following claims are not supported:

- The model is production-ready or suitable as a sole security control.
- A `LOW` answer proves a page is safe.
- The test set represents real user traffic.
- Teacher labels are verified incident labels.
- The model matches or exceeds the original service's Claude judge.
- The model has been validated on a physical device.
