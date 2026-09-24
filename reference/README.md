# Reference Materials

This directory stores materials used to understand or validate the current
prototype. Files here are not compiled into the Kotlin runtime and are not part
of the Python training package unless explicitly copied or merged elsewhere.

For the complete source/provenance explanation, see
[docs/provenance.md](../docs/provenance.md).

## Contents

| Path | Purpose |
| :-- | :-- |
| `Judge.kt` | copied reference from `SKT-FLY-AI-TEAM-6/GUADIAN/Judge.kt` |
| `HttpJudge.kt` | copied reference from `SKT-FLY-AI-TEAM-6/GUADIAN/HttpJudge.kt` |
| `WebNode.kt` | copied reference from `SKT-FLY-AI-TEAM-6/GUADIAN/WebNode.kt` |
| `judge_agent.py` | copied reference from `SKT-FLY-AI-TEAM-6/GUADIAN/judge_agent.py` |
| `docs/` | copied reference docs from `SKT-FLY-AI-TEAM-6/GUADIAN/docs` |
| `gold_251.jsonl` | sibling dataset from `/Users/jisu/guadian23-model-compare-main/gold/gold_251.jsonl`, used to derive 248 sanitized model rows |

## Usage Rule

Use `reference/` to explain where ideas and data came from. Use `android/`,
`model/`, and `server/` to inspect the current implementation.

Some links inside `reference/docs/` still point to paths from the original
GUADIAN repository. They are preserved for source context and may not resolve in
this refactored repository.
