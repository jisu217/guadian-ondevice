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

The app judges URL risk entirely on the device, with no judgment server.
Layers stay conservative:
an uncertain result is returned as `UNKNOWN` with `UNCERTAIN` confidence rather
than promoted to an overconfident verdict.

## 2. Judgment Pipeline

Every layer implements the same `Judge` interface (`suspend fun judge(url)`)
and returns a `Verdict`. `DefaultJudgeGraph` wires the layers together, and
`HybridJudge` runs them as one flat list, stopping at the first result whose
confidence is `CERTAIN`. `GuadianApp` owns a single graph, so the manual
screen and Shield share one store and one instance of every layer.

```text
URL
  -> RuleBasedJudge          BlocklistRule -> ApkRule -> BrandImpersonationRule -> SuspiciousUrlRule
  -> StoreJudge              InMemoryVerdictStore lookup
  -> LocalKnowledgeJudge
  -> PageContentJudge        manual entry only
  -> LlmJudge                manual entry only
  -> Verdict
```

The README uses concept names for the judgment stages; this document names the
concrete runtime classes. `LocalKnowledgeJudge` is the current local evidence
layer, and Local RAG is planned as an extension of that layer.

### 2.1 Components

Paths are relative to `<module>/src/main/kotlin/com/guadian/ondevice/`. The
`layer` value is what each component writes into the verdict it produces.

| Component | Module | File | Role | `layer` |
| :-- | :-- | :-- | :-- | :-- |
| `HybridJudge` | `core/judge` | `judge/HybridJudge.kt` | runs the stage list, saves certain verdicts to the store | — |
| `RuleBasedJudge` | `core/judge` | `rules/RuleBasedJudge.kt` | returns the first certain rule, else the first uncertain hint | `RULE` |
| `StoreJudge` / `InMemoryVerdictStore` | `core/judge` | `store/VerdictStore.kt` | reuses earlier verdicts for the same normalized URL | `STORE` |
| `LocalKnowledgeJudge` | `core/judge` | `knowledge/LocalKnowledgeJudge.kt` | matches local policy snippets | `KNOWLEDGE` |
| `PageContentJudge` | `core/pagecontent` | `pagecontent/PageContentJudge.kt` | inspects the rendered page in a hidden WebView | `PAGE_CONTENT` |
| `LlmJudge` | `core/llm` | `llm/LlmJudge.kt` | builds the LLM's prompt from page inspection's observation (`PromptBuilder`), runs the model (`LlmRuntime`), and reads the reply (`AnswerReader`); without a model file it stays uncertain | `LLM` |

### 2.2 Control Flow

The deterministic rules run first, before the store, so a cached verdict can
never skip them. The store is keyed by the normalized full URL (`UrlKey`:
host lowercased, `www.` and default ports dropped, fragment and click-tracking
parameters removed, path and remaining query kept), so a verdict for one path
is never reused for another path on the same host. It is a thread-safe LRU
bounded at 500 entries.

After the store, the local knowledge, page content, and LLM layers run
in turn. If none is certain, `HybridJudge` returns the last layer's
verdict. Every certain verdict whose `risk` is not `UNKNOWN` is saved to the
store, so failed or inconclusive judgments are never cached.

Each layer picks its own dispatcher: `PageContentJudge` moves to
`Dispatchers.Main` for the WebView, and callers run the pipeline on
`Dispatchers.Default`, so no layer
blocks the UI thread. The large runtime resources (allowlist, blocklist,
Public Suffix List, brand table) are preloaded off the main thread in
`GuadianApp.onCreate`.

### 2.3 Layer Order

The layers are ordered cheapest-first, so a URL resolved by a rule or by local
knowledge never triggers a page load or an LLM run. The rules, store, and
knowledge lookup do no I/O. `PageContentJudge` is
the only layer that loads the destination page, with an 8-second timeout. No
layer sends the URL or page content to a server.

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
| `category` | `RiskCategory` | `NONE` | risk category; `wireName` is the name used in fixtures |
| `host` | `String?` | `null` | judged host |
| `detail` | `String?` | `null` | variable part of the reason: a brand, matched snippet IDs, or an error message |

`confidence` has no default on purpose: every judge must decide it explicitly.

Each layer decides `CERTAIN` differently:

| Layer | Returns `CERTAIN` when |
| :-- | :-- |
| `RuleBasedJudge` | `BlocklistRule` (`HIGH`, the host or a parent domain up to the registrable domain is on the bundled phishing/malware blocklist), `ApkRule` (`HIGH`, `.apk` in path/query), official-domain match (`LOW`), a brand label as its own host token on a non-official host (`HIGH`), or a whole-token lure keyword in path/query (`SuspiciousUrlRule`, `MEDIUM`). A brand label only embedded in a longer host token is an `UNCERTAIN` `MEDIUM` hint, and is ignored when the registrable domain is allowlisted |
| `StoreJudge` | the normalized URL has a cached verdict |
| `LocalKnowledgeJudge` | a snippet matches; `risk` and `confidence` come from the first snippet |
| `PageContentJudge` | a password field on a host that is neither an official brand domain nor allowlisted: `HIGH` if the page names a known brand, otherwise `MEDIUM`. A brand name without a password field stays `UNCERTAIN` |
| `LlmJudge` | the model's reply is readable. `AnswerReader` lowers `HIGH` to `MEDIUM` unless its type is impersonation, credentials, or apk and its evidence is one of the prompt's lines verbatim; an unreadable reply is an uncertain `MEDIUM`. Without a model file it answers uncertain `UNKNOWN` |

Brand-to-domain data lives in `ml/runtime/brand_domains.tsv`, shared with the
allowlist builder, and registrable domains come from the bundled Public Suffix
List (`url/PublicSuffixList.kt`).

## 4. Entry Points

The pipeline has two entry points, which differ in what they pass in and how
the result is shown:

| Entry point | Trigger | Graph | Input | Output |
| :-- | :-- | :-- | :-- | :-- |
| `JudgeActivity` | user enters a URL manually | `full` | the entered URL | verdict screen |
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

Shield does not intervene. It shows no overlay and leaves the navigation
alone; each verdict is written to Logcat under the `GuadianShield` tag with its
risk, confidence, layer, reason, category, elapsed time, and URL. The whole
judgment has a 3-second budget, after which a timeout is logged, and the Shield
graph skips `PageContentJudge` because the browser is already loading the same
page. A new click cancels the previous judgment.

## 5. Runtime Boundary

The Android runtime is split into five Gradle modules. `core/judge` is a
plain Kotlin/JVM library holding the judgment contract and the URL-level
layers (`judge`, `rules`, `store`, `knowledge`, `url`); because it has no
Android dependency, an `android.*` import there fails to compile and its tests
run on the JVM. Each module ships only the `ml/runtime` files it reads.
`core/pagecontent` is an Android library holding only `PageContentJudge`,
the one layer that needs a WebView. `core/llm` is another plain Kotlin/JVM
library, holding the on-device LLM stage: its prompt format, chat template,
and answer reading. `core/llamacpp` is an Android library that builds llama.cpp
with the NDK and implements the stage's `LlmRuntime` on it. `app` holds the UI,
the Shield service, `GuadianApp`, and `DefaultJudgeGraph`, which wires the
layers together.

The pipeline passes only a URL between stages, so `PageContentJudge` posts a
`PageObservation` (title, visible text, form fields, APK links, status,
redirects) to an `ObservationBoard`, and `LlmJudge` takes it for the
same URL. The observation is read with the rules the training data was
collected with, and `PromptBuilder`, `ChatFormat`, and `AnswerReader` are ports
of `ml/guadian_ml/llm/contract`; parity tests hold them to Python fixtures. Every prompt is
logged under the `GuadianLlm` tag, and model load and reply times under
`GuadianLlama`.

llama.cpp is fetched at build time from a pinned release (b11216, checked by
SHA256) and compiled for arm64 with dot-product instructions, which made a
verdict about four times faster on an arm64 emulator (21 s to 4–5 s with a
4-bit 1.7B model). `CpuFeatures` keeps the library unloaded on older cores that
lack those instructions. The model file is not bundled yet; for development it
is copied into the app's private storage:

```bash
adb push guadian-qwen3-1.7b-run3-Q4_K_M.gguf /data/local/tmp/guadian.gguf
adb shell run-as com.guadian.ondevice sh -c 'mkdir -p files/models && cp /data/local/tmp/guadian.gguf files/models/guadian.gguf'
adb shell rm /data/local/tmp/guadian.gguf
```

Without the file, the LLM stage answers "not loaded" and the pipeline behaves
as before. Instrumented test runs uninstall the app afterwards, which removes
the copied model.

The implemented runtime therefore covers: the manual verdict screen, the
Shield accessibility service with Logcat output, the local rule, store,
knowledge, and page-content pipeline, and the on-device LLM stage run with
llama.cpp. Local unit tests, Robolectric tests, and CI cover the runtime and
the Python-Kotlin parity fixtures.

Physical-device validation and production readiness are not complete (see
[Technical Report](technical-report.md#10-next-steps)), and arbitrary in-app
WebViews and native ad surfaces are not covered.

## 6. Trade-offs

Each layer trades some accuracy or coverage for speed, privacy, or safety:

| Decision | Benefit | Cost |
| :-- | :-- | :-- |
| rules first | fast and explainable | rule maintenance |
| conservative rules | fewer overconfident mistakes | more cases reach slower, later layers |
| LLM last | reads what the page shows, on the device | seconds per verdict, a 1.11 GB model file |
| page content late | catches forms and brand text | page load cost; the destination server sees a visit from the device |
| accessibility Shield | judges browser clicks without touching the page | permission and surface limits; results are only logged |
