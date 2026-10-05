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
7. [Public Suffix List](#7-public-suffix-list)
8. [Phishing and Malware Blocklist](#8-phishing-and-malware-blocklist)
9. [On-device LLM Runtime and Model](#9-on-device-llm-runtime-and-model)
10. [Implementation Boundaries](#10-implementation-boundaries)
11. [Known Documentation Limitations](#11-known-documentation-limitations)

## 1. Summary

The current implementation in `app/` and `ml/` is a new
local-first prototype. It was developed while consulting earlier GUADIAN code,
GUADIAN risk-policy documents, a sibling model-comparison dataset, and a public
domain popularity list.

## 2. Reference Code From The Original GUADIAN Project

The files below are stored under `reference/original-guadian/src/` for
auditability. They are not compiled or imported by the current Kotlin/Python
runtime.

| Current file | Source repo file | Original role | How it influenced this project |
| :-- | :-- | :-- | :-- |
| `reference/original-guadian/src/Judge.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/Judge.kt` | original judge interface and verdict shape | informed the shared `Judge`/`Verdict` abstraction |
| `reference/original-guadian/src/HttpJudge.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/HttpJudge.kt` | original remote API judge client | informed `RemoteJudge` fallback shape (removed 2026-09-27) |
| `reference/original-guadian/src/WebNode.kt` | `SKT-FLY-AI-TEAM-6/GUADIAN/app/src/main/java/com/flyai/adalert/WebNode.kt` | browser accessibility-node helper (roles, `targetUrl`, host/site extraction) | adapted into `shield/WebAccessibilityNode.kt`, reduced to link detection, target-host extraction, iframe detection, and current-site inference; ad-network denylist and redirect accounting helpers not ported |
| `reference/original-guadian/src/judge_agent.py` | `SKT-FLY-AI-TEAM-6/GUADIAN/server/judge_agent.py` | original server-side judge agent | informed the fallback server boundary (removed 2026-09-27) |

The current implementation is not a direct port. The local-first sequence,
Kotlin model runtime, Python training workflow, a local knowledge fallback (removed
2026-10-05), and a FastAPI server (removed 2026-09-27) were implemented in this repository.

### 2.1 Consulted But Not Copied

- `GuADian23-model-compare/domains.py` (`host_of`, `site_of`, both diagnostic
  helpers, not part of the merged gold dataset) was read to diagnose why
  `java.net.URI` returned a null host for internationalized domains (for
  example `https://알뜰상조.com/`). The URL model's `UrlFeatureExtractor.parse()`
  (removed with the URL model) was a new implementation matching Python's
  `urllib.parse.urlparse` behavior, not a port of that file.
- `eval_set.json` (45 hand-labeled real ad-landing cases) exists identically in
  three local checkouts of the original GUADIAN app (`guadian`,
  `guadian-demo-master`, `guadian23-judge-research-master`) and was read to
  understand how the original team validated risk grades. None of its rows
  were merged into `ml/data/url/sample_urls.csv` — only `gold_251.jsonl` (from
  the separate `GuADian23-model-compare` repo) was.

## 3. Reference Policy Documents

The original GUADIAN policy documents are preserved under `reference/original-guadian/docs/`.

| Current file | Source repo file | Purpose in this project |
| :-- | :-- | :-- |
| `reference/original-guadian/docs/judge.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/judge.md` | risk category and judgment-policy reference |
| `reference/original-guadian/docs/risk-grade-rationale.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/risk-grade-rationale.md` | rationale for risk levels |
| `reference/original-guadian/docs/risk-grade-validation.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/risk-grade-validation.md` | validation framing for risk grades |
| `reference/original-guadian/docs/history.md` | `SKT-FLY-AI-TEAM-6/GUADIAN/docs/history.md` | original project context |

These documents were used as policy background. Current runtime behavior is
implemented in the `app`, `core/judge`, and `core/pagecontent` modules.

## 4. Sibling Dataset Reference

`GuADian23-model-compare` is a separate, independent experiment repository
from the same team, not the GUADIAN app repository itself. It re-runs the same
judge inputs through Claude, Gemini, and GPT-5, merges the three providers'
verdicts, and hands disagreements to a human reviewer to produce a scored gold
set (see that repo's own README for the full pipeline: `run_eval.py` ->
`merge_results.py` -> `extract_disagreements.py` -> `build_gold.py`). This
project only consumes its output file, `gold/gold_251.jsonl`; none of its
provider-comparison code was copied here.

`ml/data/raw/gold_251.jsonl` comes from that local sibling project:

```text
Repository: SKT-FLY-AI-TEAM-6/GuADian23-model-compare
Source file: gold/gold_251.jsonl
Current stored file: ml/data/raw/gold_251.jsonl
```

Recorded source identity:

```text
SHA256: 4b6c184bb58abcb945de40aa7d12fd2212a48a60b2606475b058539bd876be17
Rows: 251
```

The file contains real ad landing URL cases labeled through Claude, Gemini, and
GPT-5 comparison, with a subset manually reviewed. From this file, 248 usable
rows were merged into `ml/data/url/sample_urls.csv`; two malformed rows and one
duplicate were excluded.

The merged dataset records row-level provenance through:

- `source=reference-derived`
- `reviewed_by=human-review`
- `reviewed_by=llm-consensus-claude-gemini-openai`

This distinction matters: LLM-consensus labels are useful for prototype
evaluation, but they are not equivalent to a fully human-audited production
dataset.

## 5. Committed Dataset Provenance

The current `ml/data/url/sample_urls.csv` contains 668 rows. Provenance is
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

- 248 `reference-derived` rows came from `ml/data/raw/gold_251.jsonl`.
- The remaining `reference-derived` rows are earlier GUADIAN-policy-derived
  prototype rows. They are labeled as reference-derived because they use the
  original GUADIAN risk categories and policy documents as the source basis,
  not because they were copied verbatim from a raw production log.

The exact row-level origin should be read from `sample_urls.csv`; the table
above summarizes the committed dataset.

## 6. Public Domain Allowlist Source

`ml/runtime/official_domains.txt` was built from:

- Tranco `top-1m.csv.zip`, downloaded on 2026-09-24
- ranks 1 through 50000 only
- project-specific brand domains, now kept in `ml/runtime/brand_domains.tsv`
  (read by both `BrandDomains.kt` and the builder)
- seed domains used in the synthetic dataset

On 2026-09-25, `kakaobank.com` and `shinhansec.com` were added to
`brand_domains.tsv` and merged into the committed allowlist directly (a set
union, which is what the builder does), without re-running the builder, since
the Tranco input is not committed. `naver.me` was added to the brand file and
was already in the allowlist.

The Tranco input file is not committed. It can be re-fetched from Tranco and
processed with:

```bash
python3 ml/guadian_ml/resources/official_domains.py --tranco /path/to/top-1m.csv
```

The generated allowlist is committed because both Python feature extraction and
Kotlin runtime tests read it as a shared resource.

## 7. Public Suffix List

`ml/runtime/public_suffix_list.dat` is Mozilla's Public Suffix List,
downloaded unmodified from https://publicsuffix.org/list/public_suffix_list.dat
on 2026-09-25 (header `VERSION: 2026-09-24_13-26-36_UTC`, `COMMIT:
a179a48c465e818cfd8d626691cb317985da87fb`). It is licensed under MPL-2.0, as
its header states. `url/PublicSuffixList.kt` uses it for registrable-domain
lookup (site comparison in Shield, brand-label extraction, allowlist checks).

## 8. Phishing and Malware Blocklist

`ml/runtime/blocklist.bloom` is a Bloom filter over the ut1 phishing and
malware domain lists (https://github.com/smed79/ut1), the same lists the
original GUADIAN server checked. It was built on 2026-09-27 by
`ml/guadian_ml/resources/blocklist.py`, which drops public suffixes and
domains in `official_domains.txt` before inserting; `blocklist.meta.json`
records the sources, counts, and parameters. The lists are licensed
CC BY-NC-SA 3.0, so this file may be used for research and demonstration but
must be replaced before any commercial release. Because the license is
share-alike, `blocklist.bloom` and `blocklist_filter_cases.csv`, which are
derived from the lists, are distributed under CC BY-NC-SA 3.0 as well.

## 9. On-device LLM Runtime and Model

`core/llamacpp` compiles llama.cpp (https://github.com/ggml-org/llama.cpp,
MIT license) from release `b11216`, downloaded at build time and verified
against SHA256 `2187f9643de609a3f269ee1ae3ef4717de290546b0a4f7fe608dab51f75c8c21`;
its sources are not committed. The on-device LLM is fine-tuned from
`Qwen/Qwen3-1.7B` (Apache-2.0) with `ml/notebooks/train_llm.ipynb`; neither the
base weights nor the fine-tuned GGUF are committed.

Its training data combines the 251 gold-set URLs (section 4) with 300 URLs
from the OpenPhish community feed (https://openphish.com/feed.txt), fetched on
2026-09-26. The feed's terms (https://openphish.com/terms.html, read
2026-09-29) allow academic, independent, or personal research and forbid
commercial use and making the information or derivative works available to
third parties. Collected pages and labels therefore stay in the git-ignored
`ml/data/llm/`, and the fine-tuned model is not distributed.

URL lists for the next dataset version are built by `llm.dataset.urls`: 248
more OpenPhish URLs (feed fetched 2026-10-01, same terms), 300 `.kr` sites
from the Tranco top-1m list (downloaded 2026-10-01), and 995 Naver Search API
web results (NAVER API HUB, fetched 2026-10-02) for queries by page type. Those
are organic results, not ad clicks; ad networks were not crawled because
scripted ad clicks bill advertisers. All 1,543 were labeled by the same teacher.

## 10. Implementation Boundaries

| Area | New implementation in this repository | Reference influence |
| :-- | :-- | :-- |
| Android runtime | `app/`, `core/judge/`, `core/pagecontent/`, `core/llm/`, `core/llamacpp/` | original app and judge concepts |
| Local pipeline | `VerdictPipeline`, `VerdictTable`, `DefaultJudgeGraph` | original judge/fallback concept; the verdict table follows the identity × action grading of `reference/original-guadian/docs/risk-grade-rationale.md` |
| Rules | `rules/*` | GUADIAN risk policy docs |
| Shield link detection | `shield/WebAccessibilityNode.kt` | adapted from `reference/original-guadian/src/WebNode.kt` (source cited in its KDoc) |
| On-device LLM | `ml/guadian_ml/llm/*`, `core/llm/` | original judge criteria (`vendor/`) as teacher labels |
| Evaluation | `ml/reports/*` | new implementation |

## 11. Known Documentation Limitations

- Some reference files preserve their original wording and may not match the
  current architecture.
- Dataset labels include both human-reviewed and LLM-consensus rows.
- The rule report uses sanitized URL records, not raw browsing logs.
