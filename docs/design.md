# Verdict Design

Status: adopted 2026-10-05; being implemented. It replaces the judgment
pipeline in [Architecture](architecture.md) and the way the on-device model is
labeled and trained ([LLM Dataset](../ml/docs/llm/dataset.md)).

## Table of Contents

1. [Why Change](#1-why-change)
2. [Principles](#2-principles)
3. [Pipeline](#3-pipeline)
4. [Signals](#4-signals)
5. [Model Questions](#5-model-questions)
6. [Verdict Table](#6-verdict-table)
7. [Stage Changes](#7-stage-changes)
8. [Data, Training, and Evaluation](#8-data-training-and-evaluation)
9. [Migration](#9-migration)
10. [Decisions](#10-decisions)

## 1. Why Change

Before this design, five places in the app decided a risk, each by its own
rules: `RuleBasedJudge` (blocklist, APK URL, brand imitation, and suspicious URL
words), `LocalKnowledgeJudge`, `PageContentJudge`, `LlmJudge`, and
`AnswerReader`, which lowered some of the model's answers afterwards. A change of
criteria had to be made in each of them, and they drifted apart: the labeling
guide treated a sign-in box on an ordinary page as harmless, while
`PageContentJudge` still warned on any shown password field off the brand table.

The model is the costliest of the five. It is trained to output the risk
itself, so the criteria live in its weights, and every change of criteria means
relabeling the dataset and training again. On 2026-10-05 alone the criteria
changed three times, and the dataset was relabeled after each.

Two stages decided from words in the URL alone. `LocalKnowledgeJudge` matched
URL tokens against five fixed snippets and returned a certain verdict on any
match (`event` gave `MEDIUM`, `login` gave `HIGH`), before the page was opened;
on the 668-row URL dataset it resolved 86 rows, 55 of them correctly.
`SuspiciousUrlRule` did the same with words such as `event` and `account`.

Shield, the path for real ad clicks in a browser, ran only the rules, the store,
and local knowledge, so for most clicks the URL-word stages gave the last answer.

## 2. Principles

1. **One place decides.** A single verdict table maps signals to a risk. The
   criteria live there and nowhere else, so changing them means editing the
   table, not retraining the model.
2. **The model answers questions, not risks.** It is asked only what needs
   judgment (does the page claim to be a brand, is it a sign-in screen) and
   reports facts; the table turns them into a risk.
3. **Stop when more information cannot change the result.** The cache-and-URL
   stage and the page stage ask the table whether the result is already final,
   and skip the costly steps if so.
4. **A grade is how strongly the app steps in.** `HIGH` blocks, `MEDIUM` warns
   and leaves the choice to the user, `LOW` lets the visit through. Following
   the original service's grading
   (`reference/original-guadian/docs/risk-grade-rationale.md`), `HIGH` needs
   certainty (seen on the page), severity (account theft, money loss, device
   takeover, or the leak of information that cannot be changed), and an
   identity that is not the brand's own.

## 3. Pipeline

```text
URL
  -> Cache & URL
  -> Page
  -> On-device LLM
  -> Verdict (answer check -> verdict table)
```

When the cache-and-URL stage or the page stage settles the result, the pipeline
exits early, so the costly model inference runs only when the earlier stages
reach no conclusion.

| Stage | Part | Input | Output | Role |
| :-- | :-- | :-- | :-- | :-- |
| Cache & URL | preprocessing | URL | URL signals, or a final result | uses the stored verdict for a URL judged before; otherwise checks the blocklist and brand imitation in the host and asks the table whether the result is final |
| Page | preprocessing | URL | page signals and the prompt, or a final result | opens the page in a hidden WebView, runs `page_inspection.js`, computes the page signals, and builds the model's input; asks the table whether the result is final |
| On-device LLM | inference | prompt | answers to the six questions (JSON) | answers what needs judgment |
| Verdict | postprocessing | signals and answers | risk, type, reason, evidence | checks the answers' format and evidence, and sets the risk with the table |

The cache-and-URL and page stages decide nothing themselves; they only ask the
table in the verdict stage. The first knows only the URL; the second also knows
what the page showed.

The table is a pure function and takes well under a millisecond. The time goes
to opening the page (one to several seconds) and running the model (about four
to five seconds per verdict on the emulator; not yet measured on a phone), so a
result that is final at the cache-and-URL stage costs almost nothing, and one
final at the page stage skips the model.

A verdict carries its risk, its type, the table row that produced it, and the
evidence line, and is stored for the URL.

## 4. Signals

Signals are facts code computes without judgment. The former rules, host rules,
and page inspection already computed them; they no longer decide.

| Signal | Known at | Computed from |
| :-- | :-- | :-- |
| on the blocklist | cache & URL | bundled blocklist (`BlocklistRule`) |
| certain brand imitation in the host | cache & URL | host rules: a brand's official domain embedded under another domain (`roblox.com.mu`), or its name under another suffix (`roblox.ly`) |
| suspicious host | cache & URL | host rules: a brand name paired with an account word (`xfinityuserverification.weebly.com`) |
| official landing | page | the landing host after redirects is owned by the brand table, and is not a platform where anyone publishes (`blog.naver.com`, `docs.google.com`, and the others in the user-content list) |
| page opened | page | the page loaded with a status below 400 |
| `.apk` link | page | page inspection |
| resident registration or card number field | page | shown form fields |
| personal field | page | a shown name or phone field, or a shown form with fields other than search or email whose text names a contact field or asks consent to collect personal information |
| password field | page | shown form fields |

The official landing is known only after the page opens: an ad link usually
passes through tracking redirects, and an open redirect on an official domain
can lead anywhere.

Pages a bot defense served to the collector are left out of training and
evaluation, as now (`dataset/bot_check.py`); on the phone such a page is
treated as not opened.

## 5. Model Questions

The model reads the same kind of prompt as now (address, landing host, status,
title, visible text, form fields, APK links, brand lines, host imitation lines)
and answers six questions in one JSON line:

| Key | Question | Answer |
| :-- | :-- | :-- |
| `claims` | Does the page present itself as a brand, bank, courier, or agency other than whoever the address belongs to? | its name, with `evidence`: the prompt line that shows it; or `null` |
| `sign_in` | Is the page itself a sign-in screen? A sign-in box in the header or side of an ordinary page, or a comment password box, is not. | `true` or `false` |
| `payment` | Does it push an immediate payment or subscription without naming a seller in the shown text? | `true` or `false` |
| `investment` | Does it offer returns, side income, or trading signals? News, calculators, courses, and scam warnings do not. | `true` or `false` |
| `reward_pressure` | Is a reward offered together with time pressure? | `true` or `false` |
| `content_farm` | Is its main purpose ad clicks, by at least two of: no author or source, ads crowding the text, bait links, product lists written for search engines? | `true` or `false` |

Naming a brand, a payment method, a social login, or a partner is not a claim.
Whether the page collects personal information is not asked: code computes it
(section 4).

The answer is checked before it reaches the table. A reply that is not one JSON
object with these keys and values is a model failure, which the app shows as an
uncertain `MEDIUM`, as now. A `claims` answer whose evidence is not one of the
prompt's lines is treated as `null`.

## 6. Verdict Table

### Identity

The table first settles who the page is, from signals and the model's answer:

| Identity | When |
| :-- | :-- |
| official | official landing |
| impersonation | certain brand imitation in the host; or the page claims a brand in the brand table and the landing host is not that brand's; or it claims a brand the table does not list and is a sign-in screen or shows a resident registration or card number field |
| suspicious | suspicious host, and none of the above |
| unverifiable | the page did not open |
| unknown | none of the above |

### Risk

| Action | Official | Impersonation | Unknown |
| :-- | :-- | :-- | :-- |
| browsing only | `LOW` | `HIGH impersonation` | `LOW` |
| sign-in screen | `LOW` | `HIGH impersonation` | `MEDIUM credentials` |
| personal field | `LOW` | `HIGH impersonation` | `MEDIUM personal_info` |
| resident registration or card number field | `LOW` | `HIGH impersonation` | `HIGH credentials` |
| `.apk` link | `LOW` | `HIGH impersonation` | `HIGH apk` |
| payment push | `LOW` | `HIGH impersonation` | `MEDIUM payment` |
| investment offer | `LOW` | `HIGH impersonation` | `MEDIUM investment` |
| reward with time pressure | `LOW` | `HIGH impersonation` | `MEDIUM urgency` |
| content farm | `LOW` | `HIGH impersonation` | `MEDIUM contentfarm` |

On the blocklist is `HIGH` whatever the page shows. Suspicious is at least
`MEDIUM impersonation` and otherwise follows the unknown column. Unverifiable
is `MEDIUM unverifiable`. When several rows apply, the highest risk wins, and
its row gives the type and the reason.

### Finality

With the signals known so far, the table also reports whether the result is
final: whether any value of what is still unknown (the page, then the model's
answers) could change it. `HIGH` is always final, being the highest grade. An
official landing is final because every official cell is `LOW`. An unknown page
with a personal field is not, because a claimed brand would make it `HIGH`.
Finality comes from the table, so a change of a cell changes when the pipeline
exits early without touching any other code.

### Storage

The table is a data file, `ml/runtime/verdict_table.json`, read by the app
(`VerdictTable.kt`) and by Python (`contract/verdict_table.py`) for labels and
evaluation, as the brand table is read by both. Shared fixtures check that both
give the same verdict, and a test checks that the risk table above matches the
file (`python -m guadian_ml.llm.contract.verdict_table` prints it).

## 7. Stage Changes

| Before | After |
| :-- | :-- |
| `RuleBasedJudge` returns verdicts | removed. `BlocklistRule` gives the blocklist signal only, and brand imitation comes from `HostRules`, which the training labels use (`UrlSignals`). `SuspiciousUrlRule` and `BrandImpersonationRule` are removed |
| `StoreJudge` | removed. `VerdictPipeline` looks up the cache itself and stores each verdict with the table version |
| `LocalKnowledgeJudge` | removed |
| `PageContentJudge` warns on a shown password field | becomes `PageInspector`, which only observes; `PageFacts` computes the page signals |
| `LlmJudge` returns a risk | returns the answers; until the question model exists, it passes on the current model's risk |
| `AnswerReader` lowers `HIGH` without evidence and unsupported `personal_info` | checks format and the `claims` evidence only; the table never grants what those checks removed |
| `HybridJudge` stops at the first certain verdict | `VerdictPipeline` runs cache & URL, page, on-device LLM, and verdict in order, and exits early when the first two stages settle the result |

## 8. Data, Training, and Evaluation

The teacher (`claude-sonnet-5-5`, through the Message Batches API) answers the six
questions for every case from the student's prompt, by the
[Question Guide](../ml/docs/llm/question-guide.md); the risk rules move to the table.
Each case's risk is computed from its answers and signals by the table. Test
answers are reviewed by a person question by question, without seeing the
teacher's answer or an earlier suggestion, which also measures how far the
current human labels were anchored on suggestions.

The model is trained once on the answer format. Afterwards a change of criteria
is an edit to the table, followed by rescoring the saved answers; no relabeling
or training is needed unless a question is added or changed.

Evaluation reports three layers:

1. accuracy per question, to show which judgment is weak;
2. accuracy of the final risk after the table, against the reviewed test set;
3. scores that need no label: confirmed OpenPhish phishing answered `LOW`, and
   Tranco top sites warned on, counting pages that opened apart.

The bar for shipping stays the one set before run 5, on the relabeled split:
human-reviewed accuracy as the app reads it above run 3's (72.4% on the 156
human-reviewed cases after the 2026-10-09 review), and at most 25 missed risky pages. The guide labels of 2026-10-05 (risk labels by the labeling
guide) are kept as the fallback if this design is not adopted.

## 9. Migration

1. Settle the decisions below and adopt this document (done).
2. Write the verdict table, its Kotlin and Python readers, and shared fixtures
   (done: `ml/runtime/verdict_table.json`, `VerdictTable.kt`,
   `contract/verdict_table.py`, `fixtures/llm/verdict_cases.json`).
3. Turn the rules and page inspection into signal sources, remove
   `LocalKnowledgeJudge` and `SuspiciousUrlRule`, and replace `HybridJudge`'s
   control flow with early exit (done: `VerdictPipeline`, `UrlSignals`,
   `PageInspector`, `PageFacts`; until the question model exists, the LLM stage
   passes on the current model's risk).
4. Rewrite the labeling guide as question instructions, label the questions with
   the teacher, and review the test answers blind (done: `ml/docs/llm/question-guide.md`,
   `contract/facts.py`, the teacher's `--questions` mode; the blind review is left
   until the first question model is scored).
5. Change `AnswerReader`, `LlmJudge`, the dataset builders, the notebook, and the
   evaluation scripts to the answer format; train and score once (code done: the app
   reads both reply formats, so run 3 keeps working; the prompt and its system line
   are unchanged, so either kind of model takes the same input). Run 6 is trained
   and scored. A blind review by the project owner relabeled the 23 test cases where
   run 6 disagreed with a label a Claude model had written; on the reviewed labels
   run 6 clears the bar: 78.8% on the 156 human-reviewed cases as the app reads
   Q4_K_M (run 3: 72.4%) and 25 missed risky pages (24 with Q8_0). Shipping waits on
   timing Q4_K_M and Q8_0 on a phone.
6. Update the documents that describe the pipeline and the labels.

Page inspection, the brand and blocklist data, URL parsing, the llama.cpp
runtime, Shield's click detection, and data collection stay as they are.

## 10. Decisions

Made on 2026-10-05 by the project owner:

- The brand's own site is `LOW` whatever it shows, an `.apk` link included.
- A sign-in box on an ordinary page is not a credentials page; a page that is
  itself a sign-in screen off the brand's own site is `MEDIUM`.
- Personal information needs a shown name or phone field, or a shown form worded
  to collect contact details; wording alone, such as a consultation-request
  ("상담신청") menu link, and an email field alone do not count.
- A resident registration or card number field, or an `.apk` link, off the
  brand's own site is `HIGH`.
- A host that only pairs a brand name with an account word, on a page that
  claims nothing, is `MEDIUM`.

Settled the same day, on the recommendations in the draft:

- **Shield path.** Real ad clicks run the cache-and-URL and page stages; a
  result that is still not final is `UNKNOWN`. Whether Shield also runs the model
  is decided after the model is timed on a phone.
- **Questions.** The six questions above.
- **Sign-in screen on an unknown site.** `MEDIUM credentials`.
- **Claims of brands outside the table.** A page that claims a brand the table
  does not list (an `Orange` login on `framer.app`) is impersonation when it is a
  sign-in screen or shows a resident registration or card number field; it
  needs no extra question, and a page asking for a password or card number in
  another organization's name is the case the original grading calls `HIGH`.

Settled when step 3 started (same day):

- **Official site that did not open.** Whether a site is official is decided
  by the landing host, so a page that did not open is unverifiable
  (`MEDIUM unverifiable`). The original grading gave `LOW` to a listed address;
  this also covers phishing through an official domain's open redirect.
- **Cache and table version.** A verdict is stored with the SHA-256 of the table
  file, and judged again when the version differs.
- **One brand-imitation check.** The app's `BrandImpersonationRule`
  (`brand_domains.tsv`) is removed; only `HostRules` (`llm_brands.json`), which
  the training labels use, remains.
- **A URL that is itself an `.apk` file.** It opens no page, so the clicked
  host decides whether it is official, and the cache-and-URL stage settles it:
  `HIGH` off the brand's site, `LOW` on it.
- **The LLM stage before the question model.** It passes on the current model's
  risk.
- **URL resolution report.** Rewritten for the cache-and-URL stage
  (`./gradlew :core:llm:writeLocalResolutionReport`).

Still to check: Shield now runs the page stage, but its per-click budget is
3 seconds, which can end before the page has loaded. The budget and whether
Shield runs the model are decided together after timing on a phone.

Settled during step 4 (2026-10-06):

- **Teacher model.** On the 133 human-reviewed test cases, the risks the table
  computes from Claude Sonnet 5.5's answers match the human label on 88.0%
  (11 risky pages answered `LOW`), against 73.7% (5) for Claude Haiku 4.5's
  answers; Claude Sonnet 5.5 labels the questions. It refuses forced tool use, so
  its answers come back as structured output.
- **Claims of brands outside the table without a sign-in screen or a sensitive
  field** stay unknown. Raising them to `MEDIUM` or `HIGH` would have caught 3 more
  risky pages in the pilot but warned on 4 ordinary ones (a public wiki, a county
  government, a law firm's own name), because the teacher still reads some sites'
  own names as claims.

Known gaps, left for after the first model is trained:

- **Fake software downloads.** Pages that push a desktop installer ("PDF Viewer,
  Windows computer required") have no row: only `.apk` links count as off-store
  installs.
- **Near-empty pages.** A page that opens but shows almost nothing (a user-agent
  echo, a PHP error) is not unverifiable, since only a page that did not open is.
  In the 2026-10-09 blind review the project owner judged such pages `MEDIUM`
  unverifiable each time, which supports adding a rule for them.
- **Login forms beside a contact line.** Rule A reads a form as collecting
  personal details when contact or consent wording appears anywhere on the page,
  so a sign-in form above a footer that lists the company's 연락처 counts as a
  personal field (`nv20261002_0954`); the blind review judged that page `LOW`.

