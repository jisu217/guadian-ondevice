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
directory for the Kotlin runtime, and Python training does not import from it.

## 2. Contents

Everything here comes from the original `SKT-FLY-AI-TEAM-6/GUADIAN`
repository: code under `original-guadian/src/` and policy documents under
`original-guadian/docs/`. The four code files each informed a different part of
the current implementation:

| File | Source path | Influenced |
| :-- | :-- | :-- |
| `Judge.kt` | `app/src/main/java/com/flyai/adalert/Judge.kt` | `Judge` / `Verdict` abstraction |
| `HttpJudge.kt` | `app/src/main/java/com/flyai/adalert/HttpJudge.kt` | the former `RemoteJudge` fallback, removed when judgment moved fully on device |
| `WebNode.kt` | `app/src/main/java/com/flyai/adalert/WebNode.kt` | `shield/WebAccessibilityNode.kt` (adapted; KDoc cites `reference/original-guadian/src/WebNode.kt`) |
| `judge_agent.py` | `server/judge_agent.py` | the former fallback server, removed with `RemoteJudge` |

The same repository's `docs/` directory supplied four policy documents, stored
in `original-guadian/docs/`:
`docs/judge.md` (risk categories and judgment policy),
`docs/risk-grade-rationale.md` (rationale for risk levels),
`docs/risk-grade-validation.md` (validation framing for risk grades), and
`docs/history.md` (original project context). Some links inside them point to
paths in the original repository and may not resolve here.

The sibling-repository dataset `gold_251.jsonl` used to live here. Because it is
training input rather than reference material, it now lives at
`ml/data/raw/gold_251.jsonl` (unchanged, same SHA256); its lineage is in
[Provenance](../docs/provenance.md#4-sibling-dataset-reference).

## 3. Copy Policy

The code and doc files are verbatim copies of the originals with one addition,
a source header on the first line:

```text
Kotlin:   // 참고 출처: SKT-FLY-AI-TEAM-6/GUADIAN/<source path>
Python:   # 참고 출처: SKT-FLY-AI-TEAM-6/GUADIAN/<source path>
Markdown: > 참고 출처: `SKT-FLY-AI-TEAM-6/GUADIAN/docs/<file>`
```

`ml/data/raw/gold_251.jsonl` is stored unmodified.

## 4. Usage Rule

Use `reference/` together with [Provenance](../docs/provenance.md) to explain
where an idea or data row came from. To see how the current system behaves,
read `app/`, `core/`, and `ml/` instead.
