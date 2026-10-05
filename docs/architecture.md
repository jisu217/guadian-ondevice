# Architecture

This document describes the runtime structure of the Android app. For project
motivation, evaluation, and limitations, see
[Technical Report](technical-report.md); the reasons for this structure and the
verdict table are in [Verdict Design](design.md). The on-device LLM still grades
the risk itself until the question model of the design replaces it.

## Table of Contents

1. [Design Goal](#1-design-goal)
2. [Judgment Pipeline](#2-judgment-pipeline)
   - [2.1 Components](#21-components)
   - [2.2 Control Flow](#22-control-flow)
   - [2.3 Stage Order](#23-stage-order)
3. [Verdict Contract](#3-verdict-contract)
4. [Entry Points](#4-entry-points)
5. [Runtime Boundary](#5-runtime-boundary)
6. [Trade-offs](#6-trade-offs)

## 1. Design Goal

The app judges URL risk entirely on the device, with no judgment server. One
verdict table (`ml/runtime/verdict_table.json`) sets every risk; the stages
before it only collect facts. An uncertain result is returned as `UNKNOWN` with
`UNCERTAIN` confidence rather than promoted to an overconfident verdict.

## 2. Judgment Pipeline

`VerdictPipeline` runs the stages in order and implements the `Judge` interface
(`suspend fun judge(url)`), returning a `Verdict`. `DefaultJudgeGraph` builds two
pipelines over the same stages, and `GuadianApp` owns the graph, so the manual
screen and Shield share one store and one instance of every stage.

```text
URL
  -> Cache & URL      InMemoryVerdictStore, UrlSignals
  -> Page             PageInspector, PageFacts
  -> On-device LLM    LlmJudge (manual entry only)
  -> Verdict          VerdictTable
```

After the cache and URL stage and after the page stage, the pipeline asks the
verdict table whether the result is final, and stops there if so (early exit).

### 2.1 Components

Paths are relative to `<module>/src/main/kotlin/com/guadian/ondevice/`.

| Component | Module | File | Role |
| :-- | :-- | :-- | :-- |
| `VerdictPipeline` | `core/judge` | `verdict/VerdictPipeline.kt` | runs the stages with early exit, builds the verdict from the table's decision, saves certain verdicts to the store |
| `VerdictTable` | `core/judge` | `verdict/VerdictTable.kt` | reads `verdict_table.json`; decides a risk from the facts known so far and whether more facts could change it |
| `InMemoryVerdictStore` | `core/judge` | `store/VerdictStore.kt` | reuses earlier verdicts for the same normalized URL under the same table version |
| `UrlSignals` | `core/llm` | `llm/UrlSignals.kt` | URL facts: the blocklist (`BlocklistRule`), brand imitation in the host (`HostRules`), a URL that is itself an `.apk` file |
| `PageInspector` | `core/pagecontent` | `pagecontent/PageInspector.kt` | loads the page in a hidden WebView and runs `page_inspection.js` |
| `PageFacts` | `core/llm` | `llm/PageFacts.kt` | page facts from what the page showed: the brand's own site after redirects, opened or not, `.apk` links, registration or card number fields, personal fields |
| `LlmJudge` | `core/llm` | `llm/LlmJudge.kt` | builds the prompt (`PromptBuilder`), runs the model (`LlmRuntime`), and reads the reply (`AnswerReader`); its verdict is used as it is until the question model replaces it |

### 2.2 Control Flow

The store is checked first. It is keyed by the normalized full URL (`UrlKey`:
host lowercased, `www.` and default ports dropped, fragment and click-tracking
parameters removed, path and remaining query kept), so a verdict for one path
is never reused for another path on the same host, and each verdict is saved
with the SHA-256 of the verdict table that made it, so a verdict from an
earlier table is judged again. It is a thread-safe LRU bounded at 500 entries.

On a miss, `UrlSignals` computes the URL facts and the table decides. A result
that no page or model answer could change (a blocklisted host, certain brand
imitation, an `.apk` file) ends the judgment. Otherwise `PageInspector` loads the
page and `PageFacts` adds the page facts; an official landing, or a
registration or card number field or an `.apk` link off the brand's site, is
final here. Otherwise the LLM runs. A pipeline without the LLM stage returns an
uncertain `UNKNOWN` (`NOT_FINAL`) at that point. Every certain verdict whose
`risk` is not `UNKNOWN` is saved to the store, so failed or inconclusive
judgments are never cached.

Each stage picks its own dispatcher: `PageInspector` moves to
`Dispatchers.Main` for the WebView, and callers run the pipeline on
`Dispatchers.Default`, so no stage blocks the UI thread. The large runtime
resources (allowlist, blocklist, Public Suffix List, verdict table) are
preloaded off the main thread in `GuadianApp.onCreate`.

### 2.3 Stage Order

The stages are ordered cheapest-first. The store, the URL facts, and the table do
no I/O and take well under a millisecond; `PageInspector` is the only stage that
loads the destination page, with an 8-second timeout, and the LLM takes seconds.
No stage sends the URL or page content to a server.

## 3. Verdict Contract

All stages end in the same `Verdict` type (`judge/Verdict.kt`). Judges carry no
display text: `reason` is a code, and `ui/VerdictText.kt` turns codes into the
Korean strings in `strings.xml`.

| Field | Type | Default | Meaning |
| :-- | :-- | :-- | :-- |
| `risk` | `Risk` | — | `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN` |
| `confidence` | `Confidence` | — (required) | `CERTAIN` or `UNCERTAIN` |
| `layer` | `Layer` | — | the stage that settled it: `STORE`, `URL`, `PAGE_CONTENT`, or `LLM` |
| `reason` | `ReasonCode` | — | the table row that decided (`OFFICIAL_SITE`, `SIGN_IN_SCREEN`, …), or why no decision was made |
| `category` | `RiskCategory` | `NONE` | risk category; `wireName` is the name used in fixtures |
| `host` | `String?` | `null` | judged host |
| `detail` | `String?` | `null` | variable part of the reason: the listed domain, the official brand, a host imitation finding, or an error message |

`confidence` has no default on purpose: every judge must decide it explicitly.

A table decision is always `CERTAIN`. A verdict is `UNCERTAIN` when no decision
was made: the page stage could not read the page (an unsupported scheme or a
script failure), a pipeline without the LLM stage stopped before it
(`NOT_FINAL`), or the model's reply was unreadable or no model was loaded. Until
the question model replaces it, the LLM stage passes on `AnswerReader`'s reading
of the risk-grading model: it lowers `HIGH` to `MEDIUM` unless its type is
impersonation, credentials, or apk and its evidence is one of the prompt's lines
verbatim, and lowers a `MEDIUM` personal_info to `LOW` unless the prompt shows a
personal field, or a form with fields other than search or email together with
contact or consent wording.

Brand data for host imitation and official sites comes from
`ml/runtime/llm_brands.json`, shared with the training data, and registrable
domains come from the bundled Public Suffix List (`url/PublicSuffixList.kt`).

## 4. Entry Points

The pipeline has two entry points, which differ in what they pass in and how
the result is shown:

| Entry point | Trigger | Graph | Input | Output |
| :-- | :-- | :-- | :-- | :-- |
| `JudgeActivity` | user enters a URL manually | `full` | the entered URL | verdict screen |
| `ShieldAccessibilityService` | link click in a supported browser | `shield` (no LLM) | the link's full target URL | see below |

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
judgment has a 3-second budget, after which a timeout is logged. The Shield
pipeline runs the cache and URL stage and the page stage but not the LLM, and a
result still not final is `UNKNOWN`; whether it also runs the model is decided
once the model is timed on a phone. Loading the page can take longer than the
budget. A new click cancels the previous judgment.

## 5. Runtime Boundary

The Android runtime is split into five Gradle modules. `core/judge` is a
plain Kotlin/JVM library holding the judgment contract, the verdict table and
pipeline, the store, and URL handling (`judge`, `verdict`, `rules`, `store`,
`url`); because it has no
Android dependency, an `android.*` import there fails to compile and its tests
run on the JVM. Each module ships only the `ml/runtime` files it reads.
`core/pagecontent` is an Android library holding only `PageInspector`,
the one stage that needs a WebView. `core/llm` is another plain Kotlin/JVM
library, holding the URL and page facts and the on-device LLM stage: its prompt
format, chat template, and answer reading. `core/llamacpp` is an Android library that builds llama.cpp
with the NDK and implements the stage's `LlmRuntime` on it. `app` holds the UI,
the Shield service, `GuadianApp`, and `DefaultJudgeGraph`, which wires the
stages together.

`PageInspector` posts each `PageObservation` (title, visible text, form fields,
APK links, status, redirects) to an `ObservationBoard`, and `LlmJudge` takes it
for the same URL. `PageInspector` reads the page with `ml/runtime/page_inspection.js`,
the script the training-data collector runs too, which marks the fields the user
cannot see (a closed popup's password box) so they stay out of the prompt;
`PageSignals.observe` turns its result into the observation. `PageSignals`,
`PromptBuilder`, `ChatFormat`, and `AnswerReader` are ports of
`ml/guadian_ml/llm/contract`; parity tests hold them to Python fixtures. Every prompt is
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

Without the file, the LLM stage answers "not loaded" (an uncertain `UNKNOWN`). Instrumented test runs uninstall the app afterwards, which removes
the copied model.

The implemented runtime therefore covers: the manual verdict screen, the
Shield accessibility service with Logcat output, the cache, URL, and page
stages with the verdict table, and the on-device LLM stage run with llama.cpp. Local unit tests, Robolectric tests, and CI cover the runtime and
the Python-Kotlin parity fixtures.

Physical-device validation and production readiness are not complete (see
[Technical Report](technical-report.md#10-next-steps)), and arbitrary in-app
WebViews and native ad surfaces are not covered.

## 6. Trade-offs

Each choice trades some accuracy or coverage for speed, privacy, or safety:

| Decision | Benefit | Cost |
| :-- | :-- | :-- |
| one verdict table | criteria change in one file, without retraining; every verdict names its row | facts the table has no row for cannot raise a risk |
| early exit on a final result | no page load or LLM run when they cannot change the result | only `HIGH`, official sites, and similar cases settle early |
| URL facts without URL words | no warnings from a word like `event` in a path | fewer URLs settle before the page opens |
| LLM last | reads what the page shows, on the device | seconds per verdict, a 1.11 GB model file |
| page inspection | sees forms, links, and the landing site | page load cost; the destination server sees a visit from the device |
| accessibility Shield | judges browser clicks without touching the page | permission and surface limits; results are only logged |
