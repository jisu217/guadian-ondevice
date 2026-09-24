# Provenance

This document records what this project reused, derived, or only referenced.
The goal is to make the development lineage clear without overstating what was
copied from earlier GUADIAN work.

## Summary

The current implementation in `android/`, `model/`, and `server/` is a new
local-first prototype. It was developed while consulting earlier GUADIAN code,
GUADIAN risk-policy documents, a sibling model-comparison dataset, and a public
domain popularity list.

## Reference Code From The Original GUADIAN Project

The files below are stored under `reference/` for auditability. They are not
compiled or imported by the current Kotlin/Python runtime.

| Current file | Source repo file | Original role | How it influenced this project |
| :-- | :-- | :-- | :-- |
| `reference/Judge.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/Judge.kt` | original judge interface and verdict shape | informed the shared `Judge`/`Verdict` abstraction |
| `reference/HttpJudge.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/HttpJudge.kt` | original remote API judge client | informed `RemoteJudge` fallback shape |
| `reference/WebNode.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/WebNode.kt` | URL, host, and site extraction helper | informed URL parsing concerns |
| `reference/judge_agent.py` | `SKT-FLY-AI-TEAM-6/GUADIAN/judge_agent.py` | original server-side judge agent | informed the fallback server boundary |

The current implementation is not a direct port. The local-first sequence,
Kotlin model runtime, Python training workflow, local RAG fallback, and FastAPI
server were implemented in this repository.

## Reference Policy Documents

The original GUADIAN policy documents are preserved under `reference/docs/`.

| Current file | Source repo file | Purpose in this project |
| :-- | :-- | :-- |
| `reference/docs/judge.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/judge.md` | risk category and judgment-policy reference |
| `reference/docs/risk-grade-rationale.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/risk-grade-rationale.md` | rationale for risk levels |
| `reference/docs/risk-grade-validation.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/risk-grade-validation.md` | validation framing for risk grades |
| `reference/docs/history.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/history.md` | original project context |

These documents were used as policy background. Current runtime behavior is
implemented in `android/app/src/main/kotlin/com/guardian/ondevice/`.

## Sibling Dataset Reference

`reference/gold_251.jsonl` comes from a local sibling project:

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

## Committed Dataset Provenance

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

## Public Domain Allowlist Source

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

## Implementation Boundaries

| Area | New implementation in this repository | Reference influence |
| :-- | :-- | :-- |
| Local pipeline | `HybridJudge`, `LocalReasoningJudge` | original judge/fallback concept |
| Rules | `rules/*` | GUADIAN risk policy docs |
| Model | `model/training/*`, `OnDeviceModelJudge` | new implementation |
| Feature extraction | Python and Kotlin URL feature extractors | URL parsing concerns from reference code |
| Remote fallback | `server/main.py`, `judge_service.py`, `RemoteJudge` | original remote-judge boundary |
| Evaluation | `model/evaluation/*` | new implementation |

## Known Documentation Limitations

- Some reference files preserve their original wording and may not match the
  current architecture.
- Dataset labels include both human-reviewed and LLM-consensus rows.
- The model is evaluated on sanitized URL records, not raw browsing logs or
  complete rendered page content.
