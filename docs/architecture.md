# Architecture

This document describes the runtime structure. For project motivation,
evaluation, and limitations, see [Technical Report](technical-report.md).

## Goal

Resolve clear URL-risk cases locally and reserve remote fallback for cases where
local evidence is insufficient. Local layers should stay conservative: uncertain
results remain `UNKNOWN` rather than becoming overconfident verdicts.

## Pipeline

```text
URL
  -> LocalRiskStore
  -> RuleBasedJudge
  -> LocalReasoningJudge
       -> OnDeviceModelJudge
       -> LocalRagJudge
       -> PageContentJudge
  -> RemoteJudge
  -> Verdict
```

| Component | Role |
| :-- | :-- |
| `LocalRiskStore` | reuse previous host-level verdicts |
| `RuleBasedJudge` | handle clear URL rule cases |
| `OnDeviceModelJudge` | run the local URL-feature model |
| `LocalRagJudge` | apply local policy snippets |
| `PageContentJudge` | inspect rendered page signals in a hidden WebView |
| `RemoteJudge` | call the FastAPI fallback server |
| `HybridJudge` | orchestrate layers and return one `Verdict` |

## Verdict

All layers return the same contract:

| Field | Meaning |
| :-- | :-- |
| `risk` | `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN` |
| `reason` | explanation |
| `advice` | recommended action |
| `source` | producing component |
| `confidence` | `CERTAIN` or `UNCERTAIN` |
| `type` | risk category or runtime identifier |
| `site` | optional host/site context |

## Entry Points

| Entry point | Flow |
| :-- | :-- |
| `JudgeActivity` | user enters a URL manually |
| `ShieldAccessibilityService` | accessibility event detects a supported-browser link click |

Shield treats a click as a candidate when it leaves the inferred current site or
occurs inside an iframe. The current site is inferred from page links rather
than a browser address-bar resource id.

Configured browser packages:

```text
app/src/main/res/xml/shield_accessibility_service_config.xml
```

## Runtime Boundary

Implemented:

- Android app module under `app/`
- manual verdict screen
- Shield accessibility service and overlay
- local rule/model/RAG/page-content/remote pipeline
- generated model artifact loaded by Kotlin runtime
- local unit tests, Robolectric tests, and CI

Not complete:

- physical-device validation
- production UI, onboarding, and release packaging
- arbitrary in-app WebView or native ad-surface coverage
- production fallback-server deployment and authentication

## Trade-offs

| Decision | Benefit | Cost |
| :-- | :-- | :-- |
| rules before model | fast and explainable | rule maintenance |
| URL-feature model | cheap local inference | cannot see rendered behavior |
| conservative thresholds | fewer overconfident mistakes | more fallback traffic |
| page content late | catches forms and brand text | page load cost |
| accessibility Shield | protects browser clicks | permission and surface limits |
