# Data Policy

This project uses a sanitized prototype dataset. The dataset is useful for
engineering evaluation, but it is not production telemetry and must not be
presented as real-world phishing ground truth.

## Dataset Classes

| Class | Meaning | Allowed claim |
| :-- | :-- | :-- |
| `synthetic` | controlled rows created for feature and edge-case coverage | validates pipeline behavior and known URL patterns |
| `reference-derived` | rows derived from GUADIAN-related references or sibling gold data | exposes realistic ad-landing failure modes |
| `public-benign` | public official or normal URLs | checks benign handling and allowlist behavior |

## Review Labels

| `reviewed_by` value | Meaning |
| :-- | :-- |
| `rule-policy` | deterministic project policy label |
| `manual-review` | manually assigned or checked during prototype construction |
| `human-review` | human-reviewed row from the sibling gold data |
| `llm-consensus-claude-gemini-openai` | label from sibling multi-model consensus data |

LLM-consensus labels are treated as prototype evidence, not audited production
truth.

## Allowed Claims

- The project includes an on-device Kotlin/JVM local inference prototype.
- The model is evaluated on a deterministic stratified holdout split.
- The model is compared with a rule-scoring baseline.
- Python and Kotlin feature extraction are checked with a shared parity fixture.
- The current model is strongest on clear `HIGH` URL-level risk patterns.

## Forbidden Claims

- The model is production-ready.
- The model proves a URL is safe.
- The dataset represents real production traffic.
- LLM-consensus labels are equivalent to verified incident labels.
- The Kotlin/JVM prototype has been validated on a physical Android device.
- The local model replaces page rendering, redirect analysis, domain reputation,
  or network security tooling.

## Update Rules

When adding or changing data:

1. Keep `source`, `reason`, `category`, `reviewed_by`, and `created_at` filled.
2. Add representative Python/Kotlin feature parity rows for new URL patterns.
3. Regenerate the trained model, reports, and train/test split files.
4. Update `model/evaluation/model-card.md` if the headline metrics or limits
   change.
5. Run `scripts/check-model-governance.sh`, Python tests, server tests, and Kotlin
   tests before committing.
