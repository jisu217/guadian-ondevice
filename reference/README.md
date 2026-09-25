# Reference Materials

This directory stores source material used to understand or validate the
current prototype. Full lineage (what was reused, derived, or only consulted) is
in [Provenance](../docs/provenance.md).

## Table of Contents

1. [Build Boundary](#1-build-boundary)
2. [Contents](#2-contents)
3. [Copy Policy](#3-copy-policy)
4. [Usage Rule](#4-usage-rule)

## 1. Build Boundary

Nothing in the build reads `reference/`. It is not a Gradle source or resource
directory for the Kotlin runtime, and neither Python training nor the server
imports from it; the rows derived from the sibling dataset were merged into
`model/data/sample_urls.csv`, which is what training reads.

## 2. Contents

Four code files come from the original `SKT-FLY-AI-TEAM-6/GUADIAN` repository.
Each informed a different part of the current implementation:

| File | Source path | Influenced |
| :-- | :-- | :-- |
| `Judge.kt` | `app/src/main/java/com/flyai/adalert/Judge.kt` | `Judge` / `Verdict` abstraction |
| `HttpJudge.kt` | `app/src/main/java/com/flyai/adalert/HttpJudge.kt` | `RemoteJudge` fallback shape |
| `WebNode.kt` | `app/src/main/java/com/flyai/adalert/WebNode.kt` | `shield/WebAccessibilityNode.kt` (adapted; KDoc cites `reference/WebNode.kt`) |
| `judge_agent.py` | `server/judge_agent.py` | fallback server boundary |

The same repository's `docs/` directory supplied four policy documents:
`docs/judge.md` (risk categories and judgment policy),
`docs/risk-grade-rationale.md` (rationale for risk levels),
`docs/risk-grade-validation.md` (validation framing for risk grades), and
`docs/history.md` (original project context). Some links inside them point to
paths in the original repository and may not resolve here.

`gold_251.jsonl` comes from the sibling repository, at
`SKT-FLY-AI-TEAM-6/GuADian23-model-compare/gold/gold_251.jsonl`. It has 251
rows, SHA256 `4b6c184bb58abcb945de40aa7d12fd2212a48a60b2606475b058539bd876be17`,
and is the source of 248 sanitized rows in `model/data/sample_urls.csv`.

## 3. Copy Policy

The code and doc files are verbatim copies of the originals with one addition,
a source header on the first line:

```text
Kotlin:   // 참고 출처: SKT-FLY-AI-TEAM-6/GUADIAN/<source path>
Python:   # 참고 출처: SKT-FLY-AI-TEAM-6/GUADIAN/<source path>
Markdown: > **참고 출처:** `SKT-FLY-AI-TEAM-6/GUADIAN/docs/<file>`
```

`gold_251.jsonl` is stored unmodified.

## 4. Usage Rule

Use `reference/` together with [Provenance](../docs/provenance.md) to explain
where an idea or data row came from. To see how the current system behaves,
read `app/`, `model/`, and `server/` instead.
