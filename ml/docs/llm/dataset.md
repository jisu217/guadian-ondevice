# On-device LLM Dataset

This document describes the dataset used to train and evaluate the current
on-device Qwen3-1.7B question model. The model does not receive raw browser
state directly; it receives the same compact prompt that the Android app builds
from page inspection.

Collected page text can contain third-party content and personal data, so the
working dataset stays in git-ignored `ml/data/llm/`. Only code, fixtures,
runtime files, and generated aggregate reports are committed.

## Data Sources

The 2026-10 dataset combines:

| Source | Purpose |
| :-- | :-- |
| Original GUADIAN gold ad URLs | Real ad-landing examples from the prior project |
| OpenPhish feed URLs | Confirmed phishing coverage |
| Tranco Korean sites | High-traffic benign and mixed pages |
| Naver search results | Targeted page types such as consultation, investment, and campaign pages |

Bot-defense pages served to the collector instead of the target site are
excluded from training and evaluation.

## Final Split

After excluding 38 bot-defense cases, the final question-model dataset contains
2,056 cases. Splits are grouped by site so that test sites do not appear in
training.

| Split | Cases | LOW | MEDIUM | HIGH |
| :-- | --: | --: | --: | --: |
| Train | 1,434 | 823 | 518 | 93 |
| Validation | 208 | 102 | 90 | 16 |
| Test | 414 | 226 | 153 | 35 |

Every test case is reviewed against the model input. Of the 414 test cases, 156
are human-reviewed and 258 are reviewed by Claude Opus 5.5. Human review
overrides teacher and assistant labels.

## Pipeline

Modules live under `ml/guadian_ml/llm/`; data artifacts live under
`ml/data/llm/`.

| Module | Responsibility |
| :-- | :-- |
| `dataset.urls` | Build source URL lists from OpenPhish, Tranco, or Naver |
| `dataset.collect` | Render pages and collect page observations |
| `dataset.bot_check` | Detect collector artifacts such as access-denied or challenge pages |
| `dataset.teacher` | Ask the teacher model for six question answers or fallback guide labels |
| `dataset.final_labels` | Combine teacher answers, page facts, verdict table, reviews, and overrides |
| `dataset.split` | Build grouped train/validation/test splits |
| `dataset.sft` | Produce compact chat examples for supervised fine-tuning |
| `contract.prompt` | Python reference for the model prompt; ported to Kotlin `PromptBuilder.kt` |
| `contract.facts` | Python reference for facts consumed by the verdict table |
| `contract.answer` | Python reference for reply parsing; ported to Kotlin `AnswerReader.kt` |
| `contract.verdict_table` | Python reference for `ml/runtime/verdict_table.json` |

The contract modules are intentionally mirrored in Kotlin. Parity tests prevent
training-time prompt, answer, and table behavior from drifting away from the
Android runtime.

## Label Construction

The adopted run 6 labels are built from six factual answers, not direct risk
classification:

1. `dataset.teacher --questions` asks `claude-sonnet-5-5` to answer the
   [Question Guide](question-guide.md) from the student's own prompt.
2. `contract.facts` computes the same URL/page/model facts that the app
   computes.
3. `contract.verdict_table` maps those facts to a risk.
4. Human and assistant reviews override the computed risk where reviewed.

Each row records the teacher model and the guide SHA256 used to produce it.
This keeps label provenance explicit when the guide changes.

## Review Policy

Review is based only on the model input: address, landing host, status, title,
visible text, visible form fields, APK links, official-domain/brand lines, and
host-imitation findings. Review does not use live-page behavior observed after
collection.

Important review rules:

- a collected input is labeled as collected, not as the page might look later;
- fields hidden in closed popups or later steps do not count;
- bot checks and collector artifacts are excluded, not labeled as risky;
- human-reviewed labels override teacher and assistant labels;
- one-off corrections live in `data/llm/reviews/label_overrides.jsonl` when
  present.

## Legacy Label Sets

Runs 3 to 5 used direct-risk labels from the original criteria and later patch
rules. These paths remain in the code for reproducibility:

| Mode | Purpose |
| :-- | :-- |
| `--teacher original` | Reproduce the labels used by runs 3 to 5 |
| `--teacher guide` | Reproduce the direct-risk Labeling Guide fallback |
| `--questions` | Build the adopted run 6 question-model labels |

The legacy direct-risk labels are retained as experiment history. New model
development should use the question-answer format unless the model task itself
changes.

## Vendored Files

Teacher-label generation keeps selected source files unmodified so original
criteria can be reproduced. Each Python file carries a source header; the JSON
prompt spec is verified by SHA256.

| File | Source |
| :-- | :-- |
| `vendor/judge_agent.py` | `SKT-FLY-AI-TEAM-6/GuADian23-judge-research/server/judge_agent.py` |
| `vendor/build_prompt.py` | `SKT-FLY-AI-TEAM-6/GuADian23-model-compare/build_prompt.py` |
| `vendor/domains.py` | `SKT-FLY-AI-TEAM-6/GuADian23-model-compare/domains.py` |
| `vendor/prompt_spec.json` | `SKT-FLY-AI-TEAM-6/GuADian23-model-compare/dataset/prompt_spec.json` |

## Usage

```bash
python -m playwright install chromium
python -m guadian_ml.llm.dataset.urls naver -o ml/data/llm/sources/urls_NAME.jsonl
python -m guadian_ml.llm.dataset.collect ml/data/llm/sources/urls_NAME.jsonl -o ml/data/llm/sources/cases_NAME.jsonl
python -m guadian_ml.llm.dataset.teacher ml/data/llm/sources/cases_NAME.jsonl -o ml/data/llm/sources/labels_NAME_questions.jsonl --questions --model claude-sonnet-5-5 --batch
python -m guadian_ml.llm.dataset.final_labels
python -m guadian_ml.llm.dataset.split --keep
python -m guadian_ml.llm.dataset.sft
```

Collection and labeling append to their output and skip completed cases, so an
interrupted run can be restarted with the same command. `dataset.teacher` reads
`ANTHROPIC_API_KEY` from the environment or the repository-root `.env`.
`dataset.urls naver` also requires `NAVER_CLIENT_ID` and
`NAVER_CLIENT_SECRET`.
