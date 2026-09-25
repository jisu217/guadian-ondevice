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

Every layer implements the same `Judge` interface (`suspend fun judge(url)`)
and returns a `Verdict`. `DefaultJudgeGraph` wires the layers together, and
`HybridJudge` runs them as one flat list, stopping at the first result whose
confidence is `CERTAIN`. `GuardianApp` owns a single graph, so the manual
screen and Shield share one store and one instance of every layer.

```text
URL
  -> RuleBasedJudge          ApkRule -> BrandImpersonationRule -> SuspiciousUrlRule
  -> StoreJudge              InMemoryRiskStore lookup
  -> OnDeviceModelJudge
  -> LocalKnowledgeJudge
  -> PageContentJudge        manual entry only
  -> RemoteJudge
  -> Verdict
```

The README uses concept names for the judgment stages; this document names the
concrete runtime classes. `LocalKnowledgeJudge` is the current local evidence
layer, and Local RAG is planned as an extension of that layer.

### 2.1 Components

Paths are relative to `<module>/src/main/kotlin/com/guardian/ondevice/`. The
`layer` value is what each component writes into the verdict it produces.

| Component | Module | File | Role | `layer` |
| :-- | :-- | :-- | :-- | :-- |
| `HybridJudge` | `core/judge` | `judge/HybridJudge.kt` | runs the stage list, saves certain verdicts to the store | — |
| `RuleBasedJudge` | `core/judge` | `rules/RuleBasedJudge.kt` | returns the first certain rule, else the first uncertain hint | `RULE` |
| `StoreJudge` / `InMemoryRiskStore` | `core/judge` | `store/InMemoryRiskStore.kt` | reuses earlier verdicts for the same normalized URL | `STORE` |
| `OnDeviceModelJudge` | `core/judge` | `classifier/OnDeviceModelJudge.kt` | nearest-centroid URL-feature model | `MODEL` |
| `LocalKnowledgeJudge` | `core/judge` | `knowledge/LocalKnowledgeJudge.kt` | matches local policy snippets | `KNOWLEDGE` |
| `PageContentJudge` | `core/pagecontent` | `pagecontent/PageContentJudge.kt` | inspects the rendered page in a hidden WebView | `PAGE_CONTENT` |
| `RemoteJudge` | `core/judge` | `remote/RemoteJudge.kt` | calls `POST /judge` on the FastAPI fallback server | `REMOTE` |

### 2.2 Control Flow

The deterministic rules run first, before the store, so a cached verdict can
never skip them. The store is keyed by the normalized full URL (`UrlKey`:
host lowercased, `www.` and default ports dropped, fragment and click-tracking
parameters removed, path and remaining query kept), so a verdict for one path
is never reused for another path on the same host. It is a thread-safe LRU
bounded at 500 entries.

After the store, the model, local knowledge, page content, and remote layers
run in turn. If none is certain, `HybridJudge` returns the last layer's
verdict. Every certain verdict whose `risk` is not `UNKNOWN` is saved to the
store, so failed or inconclusive judgments are never cached.

Each layer picks its own dispatcher: `RemoteJudge` runs its blocking HTTP call
on `Dispatchers.IO`, `PageContentJudge` moves to `Dispatchers.Main` for the
WebView, and callers run the pipeline on `Dispatchers.Default`, so no layer
blocks the UI thread. The large runtime resources (allowlist, Public Suffix
List, model artifact) are preloaded off the main thread in
`GuardianApp.onCreate`.

### 2.3 Layer Order

The layers are ordered cheapest-first, so a URL resolved by a rule never
reaches the model, and a URL resolved by the model never triggers a page load.
The rules, store, model, and knowledge lookup do no I/O. `PageContentJudge` is
the only local layer that loads the destination page, with an 8-second timeout.
`RemoteJudge` is the only layer that sends the URL off the device, with a
3-second connect and 10-second read timeout. Before sending, `RemoteUrlRedactor`
drops user-info and fragment and replaces query values with `_`, keeping keys
and nested destination URLs. The endpoint comes from
`BuildConfig.REMOTE_JUDGE_URL` (`guadian.remoteJudgeUrl` in `local.properties`
or a Gradle property); when it is empty, `RemoteJudge` answers `UNKNOWN`
without touching the network.

## 3. Verdict Contract

All layers return the same `Verdict` type (`judge/Verdict.kt`). `HybridJudge`
reads only `confidence` (and `layer`, to avoid re-saving store hits) and
passes every other field through unchanged. Judges carry no display text:
`reason` is a code, and `ui/VerdictText.kt` turns codes into the Korean
strings in `strings.xml`.

| Field | Type | Default | Meaning |
| :-- | :-- | :-- | :-- |
| `risk` | `Risk` | — | `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN` |
| `confidence` | `Confidence` | — (required) | `CERTAIN` or `UNCERTAIN` |
| `layer` | `Layer` | — | producing layer (see [2.1](#21-components)) |
| `reason` | `ReasonCode` | — | why the verdict was reached |
| `category` | `RiskCategory` | `NONE` | risk category; `wireName` matches the server's `category` |
| `host` | `String?` | `null` | judged host |
| `detail` | `String?` | `null` | variable part of the reason: a brand, a model distance, or the server's reason text |

`confidence` has no default on purpose: every judge must decide it explicitly.

Each layer decides `CERTAIN` differently:

| Layer | Returns `CERTAIN` when |
| :-- | :-- |
| `RuleBasedJudge` | `ApkRule` (`HIGH`, `.apk` in path/query), official-domain match (`LOW`), a brand label as its own host token on a non-official host (`HIGH`), or a whole-token lure keyword in path/query (`SuspiciousUrlRule`, `MEDIUM`). A brand label only embedded in a longer host token is an `UNCERTAIN` `MEDIUM` hint, and is ignored when the registrable domain is allowlisted |
| `StoreJudge` | the normalized URL has a cached verdict |
| `OnDeviceModelJudge` | centroid distance is within the per-label threshold from the model artifact and the label is not `UNKNOWN`; otherwise `risk` is forced to `UNKNOWN` |
| `LocalKnowledgeJudge` | a snippet matches; `risk` and `confidence` come from the first snippet |
| `PageContentJudge` | a password field on a host that is neither an official brand domain nor allowlisted: `HIGH` if the page names a known brand, otherwise `MEDIUM`. A brand name without a password field stays `UNCERTAIN` |
| `RemoteJudge` | the server returns a `risk` other than `UNKNOWN` |

Brand-to-domain data lives in `model/runtime/brand_domains.tsv`, shared with the
allowlist builder, and registrable domains come from the bundled Public Suffix
List (`util/PublicSuffixList.kt`).

## 4. Entry Points

The pipeline has two entry points, which differ in what they pass in and how
the result is shown:

| Entry point | Trigger | Graph | Input | Output |
| :-- | :-- | :-- | :-- | :-- |
| `JudgeActivity` | user enters a URL manually | `full` | the entered URL | verdict screen with model version |
| `ShieldAccessibilityService` | link click in a supported browser | `shield` (no page content) | the link's full target URL | see below |

Shield watches Chrome, Samsung Internet, Whale, Firefox, Edge, and Opera; their
package names are listed in
`app/src/main/res/xml/shield_accessibility_service_config.xml`.

For each `TYPE_VIEW_CLICKED` event, Shield first checks that the clicked node is
a link that can carry a URL and has a target URL (`shield/WebAccessibilityNode.kt`).
A click inside an iframe is always a candidate. Any other click is a candidate
only if its target is on a different site from the current page, which Shield
infers from the majority host among the page's own top-level links rather than
from a browser address-bar resource id. Sites are compared by registrable
domain from the Public Suffix List, so `a.github.io` and `b.github.io` are
different sites.

A click event arrives after the browser has started navigating, so Shield
cannot block the navigation; it reacts. While judging, it shows a small banner
that does not take focus or touches. The whole judgment has a 3-second budget,
and the Shield graph skips `PageContentJudge` because the browser is already
loading the same page. A `HIGH` verdict triggers `GLOBAL_ACTION_BACK` and a
full-screen warning explaining that the page was left; `MEDIUM` shows the
warning without going back; `LOW`, `UNKNOWN`, and a timeout show nothing. A
new click cancels the previous judgment, so a stale result can never reopen a
dismissed warning.

## 5. Runtime Boundary

The Android runtime is split into three Gradle modules. `core/judge` is a
plain Kotlin/JVM library holding every layer that needs no Android API
(`judge`, `rules`, `store`, `classifier`, `knowledge`, `remote`, `util`) and
the `model/runtime` resources; because it has no Android dependency, an
`android.*` import there fails to compile and its tests run on the JVM.
`core/pagecontent` is an Android library holding only `PageContentJudge`,
the one layer that needs a WebView. `app` holds the UI, the Shield service,
`GuardianApp`, and `DefaultJudgeGraph`, which wires the layers together with
the app's `BuildConfig`.

The implemented runtime therefore covers: the manual verdict screen, the
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
| page content late | catches forms and brand text | page load cost; the destination server sees a visit from the device |
| accessibility Shield | protects browser clicks | permission and surface limits; reacts after navigation starts instead of blocking it |
| redacted remote requests | query values stay on the device | the server cannot use them as evidence |
