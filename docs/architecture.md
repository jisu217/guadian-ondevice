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
       -> PageContentJudge
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
| `PageContentJudge` | Render the destination page in a hidden WebView and inspect its DOM (password fields, brand-name mentions vs. actual host) | returns certain verdict on an unverified login form or a page/host brand mismatch, else `UNKNOWN` |
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
5. If local policy is still uncertain, render the destination page on-device
   and inspect it for an unverified login form or a page/host brand mismatch.
6. If page content is still uncertain, call remote fallback.
7. If remote fallback fails or is not configured, return `UNKNOWN`.

## Shield (System-wide Trigger)

`JudgeActivity` (manual URL entry) is one way to reach the pipeline above.
`ShieldAccessibilityService` is the other: an `AccessibilityService` that
watches a fixed set of browser apps and builds its own `HybridJudge` instance
(same components, same order) to judge a link the user clicked, without the
user ever opening this app.

```text
Browser link click (Chrome / Samsung Internet / Whale / Firefox / Edge / Opera)
  -> ShieldAccessibilityService.onAccessibilityEvent
       -> WebAccessibilityNode.isInsideNestedDoc: click inside an iframe?
            (yes -> treat as a candidate regardless of host)
            (no  -> WebAccessibilityNode.inferCurrentSiteHost: does the
                    target leave the site inferred from the page's own
                    top-level links?)
  -> HybridJudge (same pipeline as above)
  -> full-screen on-device overlay (TYPE_ACCESSIBILITY_OVERLAY)
```

Design notes:

- "Ad click" is approximated as "link click leaving the current site, or one
  inside an iframe" -- not "click inside a specific *ad* slot," since there is
  no ad-network host denylist in this project. An ordinary outbound link
  still resolves as `LOW` quickly through the same pipeline, so the cost of
  the broader heuristic is a few extra local judge calls, not wrong verdicts.
- The current page's site is **not** read from the browser's address bar.
  `reference/WebNode.kt`'s own comment documents why: the address bar node
  disappears from the accessibility tree entirely once the toolbar scrolls
  out of view, even in Chrome alone. Instead,
  `WebAccessibilityNode.inferCurrentSiteHost` counts the hosts linked to by
  the page's own top-level (non-iframe) links and takes the most common one
  -- the same technique the original GUADIAN's `AdScanner` used. Because it
  only reads the same chromeRole/geckoRole extras `targetHost` already uses,
  it works the same way for any Chromium- or Gecko-based browser, so
  `packageNames` in `shield_accessibility_service_config.xml` now lists six
  browsers instead of two hardcoded address-bar resource ids. A page with
  very few top-level links (the ad link itself being one of only one or two)
  can still throw this off; a richer page with normal nav/footer links is not
  affected -- verified manually, see below.
- `WebAccessibilityNode.isInsideNestedDoc` walks a clicked node's ancestors
  looking for an iframe boundary (`isNestedDoc`, ported from the original's
  `nestedDocRoles`). A click inside an iframe triggers regardless of the
  site-comparison result, since an ad iframe can point at a same-site
  redirector -- this is a real precision improvement over comparing hosts
  alone, using logic the original project had already validated.
- The link-classification helpers (`WebAccessibilityNode`) are adapted from
  `reference/WebNode.kt` (original GUADIAN bootcamp project); see that file's
  header comment for the source path. The original's ad-network denylist
  (`AdRules`) and click-accounting-safe redirect handling
  (`advertiserSiteOf`/`earlyJudgeTarget`) are not ported.
- The overlay uses `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`,
  which a bound `AccessibilityService` can add without the separate
  `SYSTEM_ALERT_WINDOW` permission a normal Activity would need for a
  draw-over-other-apps window.

**Test coverage.** `hostOf`/`siteOf`/`sameSite` are pure string logic and
plain JUnit-tested. Everything else that reads an `AccessibilityNodeInfo` --
the single-node classification functions
(`role`/`isLink`/`canBearUrl`/`targetHost`/`isNestedDoc`/`isWebHost`) *and*
the tree-walking functions (`inferCurrentSiteHost`'s child traversal,
`isInsideNestedDoc`'s ancestor walk) -- runs under **Robolectric**
(`WebAccessibilityNodeRobolectricTest`), which gives `AccessibilityNodeInfo` a
real, working implementation instead of the local `test` sourceSet's default
no-op stub. Tree fixtures are built with
`Shadows.shadowOf(parent).addChild(child)`, a Robolectric shadow-only test API
that wires real parent/child navigation between hand-built nodes (so
`getChildCount`/`getChild`/`getParent` all read from that in-memory tree
instead of attempting an IPC round-trip to a real window) -- confirmed by
reading Robolectric's own `ShadowAccessibilityNodeInfo` source rather than
assumed. This covers the majority-vote site inference, the iframe-exclusion
behavior, and the ancestor walk with fixtures a real emulator run can't
control as precisely (e.g. "exactly one ad link inside an iframe, ignored").
Only the service's end-to-end wiring (real `AccessibilityEvent` dispatch from
a real browser, the overlay window itself) remains manual-verification-only.

Manual verification performed (2026-09-24, emulator, real Chrome), rerun
after replacing the address-bar approach with `inferCurrentSiteHost`: loaded
a local test page with three same-site nav links plus one off-site link
naming a known brand (`kakaopay-login-check.test`), enabled the service via
`adb shell settings put secure enabled_accessibility_services`, and confirmed
(a) tapping the off-site link produced the full-screen overlay with a `HIGH`
/ `rule:brand-impersonation` verdict and the dismiss button removed it
cleanly, and (b) tapping a same-site nav link produced no overlay at all.
`dumpsys accessibility` confirmed the service bound with no crash both times.
This is the only layer in the project verified this way instead of (or, for
the single-node functions, in addition to) an automated test suite.

## Current Runtime Boundary

The judgment engine runs as a real Android app module (`app/`, built with
Gradle/AGP), installed and verified on an emulator. Physical-device testing
is not done yet. `ShieldAccessibilityService` recognizes six browsers (see
`shield_accessibility_service_config.xml`) but not in-app WebViews -- ads
rendered inside another app's own native UI (not a browser tab) are not
covered, since `packageNames` is restricted to browsers and there is no
"current site" concept for a native app with no address bar or page links of
its own.

## Engineering Trade-offs

| Decision | Benefit | Cost |
| :-- | :-- | :-- |
| Rules before model | Fast, explainable handling for obvious cases | rule maintenance and false-positive management |
| URL-only local model | privacy-preserving and cheap to run | cannot see page content, rendered forms, or JavaScript-only behavior |
| Conservative confidence thresholds | reduces overconfident local mistakes | sends more ambiguous cases to fallback |
| Page content rendered last, on-device | catches phishing forms/brand mismatch that URL-only layers miss, without sending the page to a server | slowest local layer (real page fetch + render); only pays that cost when URL-only layers were uncertain |
| Remote fallback | handles cases local layers cannot resolve | requires server configuration and network access |
