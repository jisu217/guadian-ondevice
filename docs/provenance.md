# Provenance

This document records what this project reused, derived, or only referenced.
The goal is to make the development lineage clear without overstating what was
copied from earlier GUADIAN work.

## Table of Contents

1. [Summary](#1-summary)
2. [Reference Code From The Original GUADIAN Project](#2-reference-code-from-the-original-guadian-project)
   - [2.1 Consulted But Not Copied](#21-consulted-but-not-copied)
3. [Reference Policy Documents](#3-reference-policy-documents)
4. [Sibling Dataset Reference](#4-sibling-dataset-reference)
5. [Committed Dataset Provenance](#5-committed-dataset-provenance)
6. [Public Domain Allowlist Source](#6-public-domain-allowlist-source)
7. [Implementation Boundaries](#7-implementation-boundaries)
8. [Known Documentation Limitations](#8-known-documentation-limitations)

## 1. Summary

The current implementation in `app/`, `model/`, and `server/` is a new
local-first prototype. It was developed while consulting earlier GUADIAN code,
GUADIAN risk-policy documents, a sibling model-comparison dataset, and a public
domain popularity list.

## 2. Reference Code From The Original GUADIAN Project

The files below are stored under `reference/` for auditability. They are not
compiled or imported by the current Kotlin/Python runtime.

| Current file | Source repo file | Original role | How it influenced this project |
| :-- | :-- | :-- | :-- |
| `reference/Judge.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/Judge.kt` | original judge interface and verdict shape | informed the shared `Judge`/`Verdict` abstraction |
| `reference/HttpJudge.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/HttpJudge.kt` | original remote API judge client | informed `RemoteJudge` fallback shape |
| `reference/WebNode.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/WebNode.kt` | browser accessibility-node helper (roles, `targetUrl`, host/site extraction) | adapted into `shield/WebAccessibilityNode.kt`, reduced to link detection, target-host extraction, iframe detection, and current-site inference; ad-network denylist and redirect accounting helpers not ported |
| `reference/judge_agent.py` | `SKT-FLY-AI-TEAM-6/GUADIAN/server/judge_agent.py` | original server-side judge agent | informed the fallback server boundary |

The current implementation is not a direct port. The local-first sequence,
Kotlin model runtime, Python training workflow, local knowledge fallback, and FastAPI
server were implemented in this repository.

### 2.1 Consulted But Not Copied

- `GuADian23-model-compare/domains.py` (`host_of`, `site_of`, both diagnostic
  helpers, not part of the merged gold dataset) was read to diagnose why
  `java.net.URI` returned a null host for internationalized domains (for
  example `https://알뜰상조.com/`). `UrlFeatureExtractor.kt`'s `parse()` function
  is a new implementation matching Python's `urllib.parse.urlparse` behavior,
  not a port of that file.
- `eval_set.json` (45 hand-labeled real ad-landing cases) exists identically in
  three local checkouts of the original GUADIAN app (`guadian`,
  `guadian-demo-master`, `guadian23-judge-research-master`) and was read to
  understand how the original team validated risk grades. None of its rows
  were merged into `model/data/sample_urls.csv` — only `gold_251.jsonl` (from
  the separate `GuADian23-model-compare` repo) was.

## 3. Reference Policy Documents

The original GUADIAN policy documents are preserved under `reference/docs/`.

| Current file | Source repo file | Purpose in this project |
| :-- | :-- | :-- |
| `reference/docs/judge.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/judge.md` | risk category and judgment-policy reference |
| `reference/docs/risk-grade-rationale.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/risk-grade-rationale.md` | rationale for risk levels |
| `reference/docs/risk-grade-validation.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/risk-grade-validation.md` | validation framing for risk grades |
| `reference/docs/history.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/history.md` | original project context |

These documents were used as policy background. Current runtime behavior is
implemented in `app/src/main/kotlin/com/guardian/ondevice/`.

## 4. Sibling Dataset Reference

`GuADian23-model-compare` is a separate, independent experiment repository
from the same team, not the GUADIAN app repository itself. It re-runs the same
judge inputs through Claude, Gemini, and GPT-5, merges the three providers'
verdicts, and hands disagreements to a human reviewer to produce a scored gold
set (see that repo's own README for the full pipeline: `run_eval.py` ->
`merge_results.py` -> `extract_disagreements.py` -> `build_gold.py`). This
project only consumes its output file, `gold/gold_251.jsonl`; none of its
provider-comparison code was copied here.

`reference/gold_251.jsonl` comes from that local sibling project:

```text
Repository name: GuADian23-model-compare
Local source checkout: /Users/jisu/guadian23-model-compare-main
Source file: gold/gold_251.jsonl
Current stored file: reference/gold_251.jsonl
```

Recorded source identity:

```text
SHA256: 4b6c184bb58abcb945de40aa7d12fd2212a48a60b2606475b058539bd876be17
Rows: 251
```

The file contains real ad landing URL cases labeled through Claude, Gemini, and
GPT-5 comparison, with a subset manually reviewed. From this file, 248 usable
rows were merged into `model/data/sample_urls.csv`; two malformed rows and one
duplicate were excluded.

The merged dataset records row-level provenance through:

- `source=reference-derived`
- `reviewed_by=human-review`
- `reviewed_by=llm-consensus-claude-gemini-openai`

This distinction matters: LLM-consensus labels are useful for prototype
evaluation, but they are not equivalent to a fully human-audited production
dataset.

## 5. Committed Dataset Provenance

The current `model/data/sample_urls.csv` contains 668 rows. Provenance is
recorded row-by-row through `source`, `reviewed_by`, and `created_at`.

Current `source` distribution:

| Source | Rows | Meaning |
| :-- | --: | :-- |
| `synthetic` | 301 | controlled samples generated for feature and pipeline validation |
| `reference-derived` | 275 | samples derived from GUADIAN-related references or the sibling gold dataset |
| `public-benign` | 92 | public official/normal URLs used as benign examples |

Current `reviewed_by` distribution:

| Reviewed by | Rows | Meaning |
| :-- | --: | :-- |
| `manual-review` | 337 | manually assigned or manually checked during prototype dataset construction |
| `llm-consensus-claude-gemini-openai` | 217 | label from sibling LLM-consensus gold data |
| `rule-policy` | 83 | label assigned by deterministic project policy |
| `human-review` | 31 | human-reviewed rows from the sibling gold data |

Important split:

- 248 `reference-derived` rows came from `reference/gold_251.jsonl`.
- The remaining `reference-derived` rows are earlier GUADIAN-policy-derived
  prototype rows. They are labeled as reference-derived because they use the
  original GUADIAN risk categories and policy documents as the source basis,
  not because they were copied verbatim from a raw production log.

The exact row-level origin should be read from `sample_urls.csv`; the table
above summarizes the committed dataset.

## 6. Public Domain Allowlist Source

`model/data/official_domains.txt` was built from:

- Tranco `top-1m.csv.zip`, downloaded on 2026-09-24
- ranks 1 through 50000 only
- project-specific brand domains from `OfficialDomains.kt`
- seed domains used in the synthetic dataset

The Tranco input file is not committed. It can be re-fetched from Tranco and
processed with:

```bash
python3 model/training/build_official_domains.py --tranco /path/to/top-1m.csv
```

The generated allowlist is committed because both Python feature extraction and
Kotlin runtime tests read it as a shared resource.

## 7. Implementation Boundaries

| Area | New implementation in this repository | Reference influence |
| :-- | :-- | :-- |
| Android runtime | `app/src/main/kotlin/com/guardian/ondevice/*` | original app and judge concepts |
| Local pipeline | `HybridJudge`, `LocalReasoningJudge` | original judge/fallback concept |
| Rules | `rules/*` | GUADIAN risk policy docs |
| Shield link detection | `shield/WebAccessibilityNode.kt` | adapted from `reference/WebNode.kt` (source cited in its KDoc) |
| Model | `model/training/*`, `OnDeviceModelJudge` | new implementation |
| Feature extraction | Python and Kotlin URL feature extractors | URL parsing concerns from reference code |
| Remote fallback | `server/main.py`, `judge_service.py`, `RemoteJudge` | original remote-judge boundary |
| Evaluation | `model/evaluation/*` | new implementation |

## 8. Known Documentation Limitations

- Some reference files preserve their original wording and may not match the
  current architecture.
- Dataset labels include both human-reviewed and LLM-consensus rows.
- The model is evaluated on sanitized URL records, not raw browsing logs or
  complete rendered page content.
