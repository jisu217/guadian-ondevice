# Architecture

This document describes the runtime judgment architecture. For project
motivation, data provenance, and model evaluation, see
[Project Report](project-report.md).

## Design Goal

The pipeline minimizes network use by resolving clear URL-risk decisions locally.
Remote fallback is reserved for cases where local evidence is insufficient.

```text
URL input
  -> LocalRiskStore
  -> RuleBasedJudge
  -> LocalReasoningJudge
       -> OnDeviceModelJudge
       -> LocalRagJudge
  -> RemoteJudge
  -> Verdict
```

## Components

| Component | Responsibility | Output behavior |
| :-- | :-- | :-- |
| `LocalRiskStore` | Reuse previous verdicts by normalized host | returns cached verdict or miss |
| `RuleBasedJudge` | Apply deterministic rules for official domains, APK downloads, brand impersonation, and suspicious URL tokens | returns certain verdict when rule evidence is clear |
| `OnDeviceModelJudge` | Run local URL-feature classifier | returns certain verdict when label distance passes calibrated threshold |
| `LocalRagJudge` | Retrieve local policy snippets for known risk patterns | returns policy-backed verdict or `UNKNOWN` |
| `RemoteJudge` | Call the FastAPI fallback server | returns remote verdict or safe `UNKNOWN` on failure |
| `HybridJudge` | Orchestrate the sequence and cache reusable verdicts | exposes the unified `Judge` interface |

## Verdict Contract

`Verdict` is the shared output type across all layers.

| Field | Meaning |
| :-- | :-- |
| `risk` | `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN` |
| `reason` | user-facing explanation |
| `advice` | recommended action |
| `source` | component that produced the verdict |
| `confidence` | `CERTAIN` or `UNCERTAIN` |
| `type` | risk category or model/runtime identifier |
| `site` | optional host/site context |

## Fallback Policy

1. If the local store has a reusable verdict, return it.
2. If deterministic rules produce a certain verdict, return it.
3. If rules are uncertain, run local model inference.
4. If model confidence is insufficient, run local policy retrieval.
5. If local policy is still uncertain, call remote fallback.
6. If remote fallback fails or is not configured, return `UNKNOWN`.

## Current Runtime Boundary

The code is implemented as Kotlin/JVM under `android/app/src/...`, not as a
complete Android application module. This allows the judgment engine to be
tested locally before Android UI, URL capture, and device integration are added.

## Engineering Trade-offs

| Decision | Benefit | Cost |
| :-- | :-- | :-- |
| Rules before model | Fast, explainable handling for obvious cases | rule maintenance and false-positive management |
| URL-only local model | privacy-preserving and cheap to run | cannot see page content, rendered forms, or JavaScript-only behavior |
| Conservative confidence thresholds | reduces overconfident local mistakes | sends more ambiguous cases to fallback |
| Remote fallback | handles cases local layers cannot resolve | requires server configuration and network access |
