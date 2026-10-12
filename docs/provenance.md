# Provenance

This document records what GUADIAN On-device reuses, derives, references, or
excludes. The current implementation is a new local-first prototype; preserved
reference files exist for auditability and are not compiled into the runtime.

## Summary

| Area | Status |
| :-- | :-- |
| Android runtime | New implementation in `app/` and `core/` |
| ML pipeline | New implementation in `ml/guadian_ml/` |
| Original GUADIAN code | Referenced or partially adapted; preserved under `reference/` |
| Original policy docs | Referenced for risk categories and grading principles |
| Sibling model-comparison dataset | Used through its gold output file only |
| Runtime third-party data | Public suffix list, Tranco-derived allowlist, ut1-derived blocklist |
| Model weights | Base and fine-tuned weights are not committed or distributed |

## Original GUADIAN References

Files preserved under `reference/original-guadian/src/` are reference material.
They are not imported by the current Kotlin or Python runtime.

| Preserved file | Original role | Use in this project |
| :-- | :-- | :-- |
| `Judge.kt` | Judge interface and verdict shape | Informed the shared `Judge`/`Verdict` abstraction |
| `HttpJudge.kt` | Remote API judge client | Informed a removed remote fallback boundary |
| `WebNode.kt` | Browser accessibility-node helper | Adapted into `shield/WebAccessibilityNode.kt` with narrower scope |
| `judge_agent.py` | Server-side LLM judge | Used as policy and prompt reference for teacher labeling |

The project also consulted `GuADian23-model-compare/domains.py` to diagnose URL
parsing behavior for internationalized domains. The removed URL feature parser
was a new implementation, not a port.

## Policy Documents

The original GUADIAN policy documents are preserved under
`reference/original-guadian/docs/`.

| File | Purpose |
| :-- | :-- |
| `judge.md` | Risk category and judgment-policy reference |
| `risk-grade-rationale.md` | Rationale for `LOW`/`MEDIUM`/`HIGH` grading |
| `risk-grade-validation.md` | Validation framing for risk grades |
| `history.md` | Original project context |

Current runtime behavior is implemented in `app/`, `core/`, and `ml/runtime/`;
the reference policy documents do not define runtime behavior by themselves.

## Sibling Dataset

`GuADian23-model-compare` is a separate experiment repository from the same
team. This project uses only its output file:

```text
Repository: SKT-FLY-AI-TEAM-6/GuADian23-model-compare
Source file: gold/gold_251.jsonl
Current file: ml/data/raw/gold_251.jsonl
SHA256: 4b6c184bb58abcb945de40aa7d12fd2212a48a60b2606475b058539bd876be17
Rows: 251
```

From that file, 248 usable rows were merged into
`ml/data/url/sample_urls.csv`; two malformed rows and one duplicate were
excluded. The provider-comparison code from the sibling repository was not
copied.

## Committed Dataset Provenance

`ml/data/url/sample_urls.csv` contains 668 URL-level rows. Row-level provenance
is recorded through `source`, `reviewed_by`, and `created_at`.

| Source | Rows | Meaning |
| :-- | --: | :-- |
| `synthetic` | 301 | Controlled examples for feature and pipeline validation |
| `reference-derived` | 275 | Rows derived from GUADIAN references or the sibling gold dataset |
| `public-benign` | 92 | Public official or normal URLs used as benign examples |

| Reviewed by | Rows | Meaning |
| :-- | --: | :-- |
| `manual-review` | 337 | Assigned or checked during prototype dataset construction |
| `llm-consensus-claude-gemini-openai` | 217 | Label from sibling LLM-consensus gold data |
| `rule-policy` | 83 | Deterministic project-policy label |
| `human-review` | 31 | Human-reviewed rows from the sibling gold data |

LLM-consensus rows are useful for prototype evaluation, but they are not a
fully human-audited production dataset.

## Runtime Data Sources

| Runtime file | Source | Notes |
| :-- | :-- | :-- |
| `ml/runtime/official_domains.txt` | Tranco `top-1m.csv.zip` downloaded 2026-09-24, ranks 1-50000, plus project brand domains and synthetic seeds | Tranco input is not committed |
| `ml/runtime/brand_domains.tsv` | Project-maintained brand domains | Read by both Python and Kotlin builders |
| `ml/runtime/public_suffix_list.dat` | Mozilla Public Suffix List downloaded 2026-09-25 | MPL-2.0; used for registrable-domain lookup |
| `ml/runtime/blocklist.bloom` | ut1 phishing and malware domain lists | CC BY-NC-SA 3.0; research/demo use only |
| `ml/runtime/blocklist.meta.json` | Generated metadata for the Bloom filter | Records source counts and parameters |
| `ml/runtime/page_inspection.js` | Project code | Run by the Android WebView and the dataset collector |
| `ml/runtime/verdict_table.json` | Project policy table | Final risk decision table |

Because the ut1 lists are CC BY-NC-SA 3.0, derived blocklist artifacts must be
replaced before any commercial release.

## LLM Runtime And Model

`core/llamacpp` builds llama.cpp from release `b11216` at build time and
verifies SHA256
`2187f9643de609a3f269ee1ae3ef4717de290546b0a4f7fe608dab51f75c8c21`. llama.cpp
is MIT licensed; its sources are not committed.

The on-device model is fine-tuned from `Qwen/Qwen3-1.7B` (Apache-2.0). Neither
the base weights nor the fine-tuned GGUF are committed.

The LLM dataset combines the sibling gold URLs with OpenPhish, Tranco, and
Naver Search API results. OpenPhish feed terms allow academic, independent, or
personal research and restrict commercial use and redistribution of derivative
works. For that reason:

- collected pages and labels stay in git-ignored `ml/data/llm/`;
- the fine-tuned GGUF is not distributed;
- generated reports expose aggregate results rather than page contents.

## Implementation Boundaries

| Component | Current implementation | Reference influence |
| :-- | :-- | :-- |
| Android app | `app/` | New |
| Judgment pipeline | `core/judge/` | Original judge/fallback concept |
| Page inspection | `core/pagecontent/`, `ml/runtime/page_inspection.js` | New |
| Shield link detection | `core/shield/` | Narrow adaptation from original `WebNode.kt` |
| LLM contract | `core/llm/`, `ml/guadian_ml/llm/contract/` | Original criteria used for teacher-label history |
| llama.cpp bridge | `core/llamacpp/` | New integration over llama.cpp |
| Evaluation | `ml/reports/` | New generated reports |

## Known Limitations

- Some reference files preserve original wording and do not describe the current
  architecture.
- Dataset labels mix human review, assistant review, teacher labels, and
  deterministic policy outputs.
- `sample_urls.csv` is a sanitized prototype dataset, not raw browsing logs.
- The project should not be presented as a production security system or as a
  fully redistributed model package.
