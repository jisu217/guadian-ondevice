# Reference Materials

This directory stores materials used to understand or validate the current
prototype. Files here are not compiled into the Kotlin runtime and are not part
of the Python training package unless explicitly copied or merged elsewhere.

For the complete source/provenance explanation, see
[docs/source-and-reference.md](../docs/source-and-reference.md).

## Contents

| Path | Purpose |
| :-- | :-- |
| `Judge.kt` | original GUADIAN judge/verdict reference |
| `HttpJudge.kt` | original remote judge client reference |
| `WebNode.kt` | original URL/site parsing reference |
| `judge_agent.py` | original server judge-agent reference |
| `docs/` | original risk-policy and validation notes |
| `gold_251.jsonl` | sibling gold dataset used to derive 248 sanitized model rows |

## Usage Rule

Use `reference/` to explain where ideas and data came from. Use `android/`,
`model/`, and `server/` to inspect the current implementation.
