# Architecture

This document describes the runtime structure. For project motivation,
evaluation, and limitations, see [Technical Report](technical-report.md).

## Goal

Resolve clear URL-risk cases locally and reserve remote fallback for cases where
local evidence is insufficient. Local layers should stay conservative: uncertain
results remain `UNKNOWN` rather than becoming overconfident verdicts.

## Pipeline

Every layer implements the same `Judge` interface and returns a `Verdict`
tagged `CERTAIN` or `UNCERTAIN`. `HybridJudge` walks the layers in order and
stops at the first `CERTAIN` result, so a layer only runs when everything
before it failed to resolve the URL with confidence:

```text
URL
  -> InMemoryRiskStore
  -> RuleBasedJudge
  -> LocalReasoningJudge
       -> OnDeviceModelJudge
       -> LocalKnowledgeJudge
       -> PageContentJudge
  -> RemoteJudge
  -> Verdict
```

The order is deliberately cheapest-first: the store and rule engine are
near-instant string checks, the model and local knowledge lookup run in
microseconds, `PageContentJudge` is the only local layer that pays the cost of
actually loading a WebView, and `RemoteJudge` is the only layer that leaves the
device at all. A URL that a rule can resolve never reaches the model, and a
URL the model resolves never triggers a page load.

| Component | Role |
| :-- | :-- |
| `InMemoryRiskStore` | reuse previous host-level verdicts during the app process |
| `RuleBasedJudge` | handle clear URL rule cases |
| `LocalReasoningJudge` | run local model, policy, and page-content layers |
| `OnDeviceModelJudge` | run the local URL-feature model |
| `LocalKnowledgeJudge` | apply local policy snippets |
| `PageContentJudge` | inspect rendered page signals in a hidden WebView |
| `RemoteJudge` | call the FastAPI fallback server |
| `HybridJudge` | orchestrate layers and return one `Verdict` |

## Verdict

Every layer, local or remote, returns the exact same `Verdict` shape rather
than a layer-specific result type. This is what lets `HybridJudge` treat a
rule match, a model prediction, and a Claude response identically -- it only
ever needs to check `confidence` and pass the rest through unchanged:

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

- `JudgeActivity`: user enters a URL manually
- `ShieldAccessibilityService`: accessibility event detects a supported-browser link click

Shield treats a click as a candidate when it leaves the inferred current site or
occurs inside an iframe. The current site is inferred from page links rather
than a browser address-bar resource id.

Configured browser packages:

```text
app/src/main/res/xml/shield_accessibility_service_config.xml
```

## Runtime Boundary

The implemented runtime lives primarily under `app/`. It includes the manual
verdict screen, the Shield accessibility service and overlay, the local
rule/model/knowledge/page-content pipeline, the remote fallback client, and Kotlin
loading of the generated model artifact. Local unit tests, Robolectric tests,
and CI cover the main runtime and model documentation paths.

Not complete:

- physical-device validation and production readiness; see
  [Technical Report](technical-report.md#next-steps)
- arbitrary in-app WebView or native ad-surface coverage

## Trade-offs

Each layer trades some accuracy or coverage for speed, privacy, or safety.
None of these are free wins -- they are documented here so a reviewer can see
what was given up, not just what was gained:

| Decision | Benefit | Cost |
| :-- | :-- | :-- |
| rules before model | fast and explainable | rule maintenance |
| URL-feature model | cheap local inference | cannot see rendered behavior |
| conservative thresholds | fewer overconfident mistakes | more fallback traffic |
| page content late | catches forms and brand text | page load cost |
| accessibility Shield | protects browser clicks | permission and surface limits |
