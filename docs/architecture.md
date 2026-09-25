# Architecture

This document describes the runtime structure of the Android app. For project
motivation, evaluation, and limitations, see
[Technical Report](technical-report.md).

## Table of Contents

1. [Design Goal](#1-design-goal)
2. [Judgment Pipeline](#2-judgment-pipeline)
   - [2.1 Components](#21-components)
   - [2.2 Control Flow](#22-control-flow)
   - [2.3 Layer Order](#23-layer-order)
3. [Verdict Contract](#3-verdict-contract)
4. [Entry Points](#4-entry-points)
5. [Runtime Boundary](#5-runtime-boundary)
6. [Trade-offs](#6-trade-offs)

## 1. Design Goal

The app resolves clear URL-risk cases locally and reserves the remote fallback
for cases where local evidence is insufficient. Local layers stay conservative:
an uncertain result is returned as `UNKNOWN` with `UNCERTAIN` confidence rather
than promoted to an overconfident verdict.

## 2. Judgment Pipeline

Every layer implements the same `Judge` interface and returns a `Verdict`.
`DefaultJudgeGraph` wires the layers together, and `HybridJudge` runs them in
order, stopping at the first result whose confidence is `CERTAIN`.

```text
URL
  -> InMemoryRiskStore
  -> RuleBasedJudge          ApkRule -> BrandImpersonationRule -> SuspiciousUrlRule
  -> LocalReasoningJudge
       -> OnDeviceModelJudge
       -> LocalKnowledgeJudge
       -> PageContentJudge
  -> RemoteJudge
  -> Verdict
```

The README uses concept names for the judgment stages; this document names the
concrete runtime classes. `LocalKnowledgeJudge` is the current local evidence
layer, and Local RAG is planned as an extension of that layer.

### 2.1 Components

Paths are relative to `app/src/main/kotlin/com/guardian/ondevice/`. The
`source` tag is the value each component writes into the verdict it produces.

| Component | File | Role | `source` tag |
| :-- | :-- | :-- | :-- |
| `HybridJudge` | `judge/HybridJudge.kt` | orchestrates layers, writes reusable verdicts to the store | — |
| `InMemoryRiskStore` | `store/InMemoryRiskStore.kt` | reuses previous verdicts for the app process lifetime | `local-store` |
| `RuleBasedJudge` | `judge/RuleBasedJudge.kt` | returns the first matching deterministic rule | `rule:*`, `rule-engine` |
| `LocalReasoningJudge` | `judge/LocalReasoningJudge.kt` | runs local sub-judges in order | `local-reasoning` |
| `OnDeviceModelJudge` | `model/OnDeviceModelJudge.kt` | nearest-centroid URL-feature model | `on-device-model` |
| `LocalKnowledgeJudge` | `knowledge/LocalKnowledgeJudge.kt` | matches local policy snippets | `local-knowledge` |
| `PageContentJudge` | `webview/PageContentJudge.kt` | inspects the rendered page in a hidden WebView | `page-content` |
| `RemoteJudge` | `judge/RemoteJudge.kt` | calls `POST /judge` on the FastAPI fallback server | `remote` |

### 2.2 Control Flow

`HybridJudge` first looks up the URL's host in the store. On a hit it returns
the cached verdict immediately, relabeled with `source = local-store`. If the
host parses to nothing, the trimmed, lowercased URL is used as the key instead.

On a miss, `RuleBasedJudge` runs; a `CERTAIN` verdict is returned. Otherwise
`LocalReasoningJudge` tries the model, local knowledge, and page content in
turn and stops at the first `CERTAIN` sub-verdict. If none is certain, it
passes on the last sub-judge's verdict and `HybridJudge` calls `RemoteJudge`,
whose result is returned whatever its confidence.

Any returned verdict whose `risk` is not `UNKNOWN` is saved to the store, so
failed or inconclusive judgments are never cached. A remote HTTP error,
timeout, or unparseable body therefore yields an uncached `UNKNOWN` /
`UNCERTAIN` verdict.

### 2.3 Layer Order

The layers are ordered cheapest-first, so a URL resolved by a rule never
reaches the model, and a URL resolved by the model never triggers a page load.
The store, rules, model, and knowledge lookup do no I/O. `PageContentJudge` is
the only local layer that loads the destination page, with an 8-second timeout.
`RemoteJudge` is the only layer that sends the URL off the device; it uses a
3-second connect and 10-second read timeout, and the server behind it calls
Claude when an API key is configured.

## 3. Verdict Contract

All layers return the same `Verdict` type (`judge/Verdict.kt`). `HybridJudge`
reads only `confidence` and passes every other field through unchanged, which
is what lets a rule match, a model prediction, and a remote response be handled
identically.

| Field | Type | Default | Meaning |
| :-- | :-- | :-- | :-- |
| `risk` | `Risk` | — | `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN` |
| `reason` | `String` | — | explanation |
| `advice` | `String` | — | recommended action |
| `source` | `String` | — | producing component (see [2.1](#21-components)) |
| `confidence` | `Confidence` | `CERTAIN` | `CERTAIN` or `UNCERTAIN` |
| `site` | `String?` | `null` | host/site context |
| `type` | `String` | `"none"` | risk category or runtime identifier |

Each layer decides `CERTAIN` differently:

| Layer | Returns `CERTAIN` when |
| :-- | :-- |
| `RuleBasedJudge` | `ApkRule` (`HIGH`), official-domain match (`LOW`), brand in a non-official host (`HIGH`), or `SuspiciousUrlRule` (`MEDIUM`) matches |
| `OnDeviceModelJudge` | centroid distance is within the per-label threshold from the model artifact and the label is not `UNKNOWN`; otherwise `risk` is forced to `UNKNOWN` |
| `LocalKnowledgeJudge` | a snippet matches; `risk` and `confidence` come from the first snippet |
| `PageContentJudge` | the page mentions a known brand on a non-official host (`HIGH`), or shows a password field on a host with no official-domain match (`MEDIUM`) |
| `RemoteJudge` | the server returns a `risk` other than `UNKNOWN` |

## 4. Entry Points

The pipeline has two entry points, which differ in what they pass in and how
the result is shown:

| Entry point | Trigger | Input to `HybridJudge` | Output |
| :-- | :-- | :-- | :-- |
| `JudgeActivity` | user enters a URL manually | the entered URL | verdict screen |
| `ShieldAccessibilityService` | link click in a supported browser | `https://<target host>` | full-screen `TYPE_ACCESSIBILITY_OVERLAY` |

Shield watches Chrome, Samsung Internet, Whale, Firefox, Edge, and Opera; their
package names are listed in
`app/src/main/res/xml/shield_accessibility_service_config.xml`.

For each `TYPE_VIEW_CLICKED` event, Shield first checks that the clicked node is
a link that can carry a URL and has a target host (`shield/WebAccessibilityNode.kt`).
A click inside an iframe is always a candidate. Any other click is a candidate
only if its target host differs from the current site, which Shield infers from
the majority host among the page's own top-level links rather than from a
browser address-bar resource id, so the check does not depend on
browser-specific view ids. For a candidate, Shield shows a loading overlay, runs
`HybridJudge`, and replaces the overlay with the verdict.

## 5. Runtime Boundary

The implemented runtime lives under `app/`: the manual verdict screen, the
Shield accessibility service and overlay, the local rule, model, knowledge, and
page-content pipeline, the remote fallback client, and Kotlin loading of the
generated model artifact. Local unit tests, Robolectric tests, and CI cover the
main runtime and model documentation paths.

Physical-device validation and production readiness are not complete (see
[Technical Report](technical-report.md#10-next-steps)), and arbitrary in-app
WebViews and native ad surfaces are not covered.

## 6. Trade-offs

Each layer trades some accuracy or coverage for speed, privacy, or safety:

| Decision | Benefit | Cost |
| :-- | :-- | :-- |
| rules before model | fast and explainable | rule maintenance |
| URL-feature model | cheap local inference | cannot see rendered behavior |
| conservative thresholds | fewer overconfident mistakes | more fallback traffic |
| page content late | catches forms and brand text | page load cost |
| accessibility Shield | protects browser clicks | permission and surface limits |
