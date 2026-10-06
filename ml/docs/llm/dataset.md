# On-device LLM Dataset

Builds the training and evaluation data for the on-device Qwen judge (#11).

`dataset.urls` builds URL lists, and `dataset.collect` gathers, for each
landing-page URL, the evidence a judge needs: rendered page text, HTML signals,
blocklist membership, official-domain lookups, and domain age, plus the result
of the app's page inspection script for the model's input. `dataset.teacher` has a
teacher model (`claude-sonnet-5-5`) answer the six questions of the
[Question Guide](question-guide.md) for each case, reading the same prompt the
on-device model reads, and the verdict table of the
[Verdict Design](../../../docs/design.md) turns the answers and the page's facts into a
risk. Human review corrects the risk of reviewed cases.
Claude is used only here, offline; the app does not call it.

## Pipeline

Modules are under `guadian_ml/llm/`; inputs and outputs are under
`data/llm/`, which is excluded from git because collected page text contains
third-party content and personal data. Per-source URL lists, collected cases,
and teacher labels are in `sources/`; review files in `reviews/`; the merged
labels, split, and training examples in `final/`; model replies from scoring in
`eval/`; and the blocklist download, domain-age database, and tokenizer in
`cache/` (`guadian_ml/paths.py` names each).

| Module | Step |
| :-- | :-- |
| `dataset.urls` | Naver search, Tranco, or OpenPhish → `urls_*.jsonl`, skipping sites already listed |
| `dataset.collect` | URL list → `cases_*.jsonl` |
| `dataset.bot_check` | recognizes pages a bot defense served to the collector, which are left out |
| `dataset.teacher` | `cases_*.jsonl` → `labels_*_questions.jsonl` (`--questions`); `labels_*_guide.jsonl` (`--guide`, the fallback guide labels); the original criteria → `labels_*.jsonl` (runs 3 to 5) |
| `dataset.final_labels` | the teacher's answers + facts + the verdict table + human review + assistant review + overrides → `labels_final.jsonl`, and bot-check pages → `excluded.jsonl` |
| `dataset.split` | `labels_final.jsonl` → `splits.jsonl` (train / validation / test, grouped by site) |
| `dataset.sft` | labels + cases → `sft_{train,validation,test}.jsonl`, compact chat examples |
| `contract.prompt` | the model's input: `PageObservation` → prompt, ported to `PromptBuilder.kt` |
| `contract.host_rules` | host-only brand-imitation grading, used by the facts and the prompt |
| `contract.facts` | the verdict table's facts from a URL, a page, and the model's answers, as the app computes them (`UrlSignals.kt`, `PageFacts.kt`, `LlmBrands.claimKind`) |
| `contract.verdict_table` | the verdict table (`ml/runtime/verdict_table.json`, `VerdictTable.kt`) |
| `contract.answer` | the app's reply reading (`AnswerReader.kt`): a question model's answers with the claim's evidence check, or a risk model's risk with its grounding and personal_info checks |
| `vendor/` | unmodified files from the original repositories |

## Labels

The labels in force, adopted on 2026-10-06, are the teacher's answers to the six
questions of the [Question Guide](question-guide.md) (docs/design.md, section 5).
`claude-sonnet-5-5` answers them from the student's own prompt
(`dataset.teacher --questions --model claude-sonnet-5-5`), and a claim stands only
on a prompt line naming the organization; each row records the model and the
SHA256 of the guide text it read. `dataset.final_labels` computes the page's facts
with `contract.facts`, the same signals the app computes, and the verdict table turns
the answers and the facts into each case's risk; no other rule runs. The answers are
the training target and the risk is the label a model is scored against, so a
reviewed case keeps its reviewed risk. Pages a bot defense served to the collector
instead of the site (Akamai "Access Denied", Cloudflare challenges, and similar, with
an error status) are left out of training and evaluation (`dataset.bot_check`): 38
cases, listed in `final/excluded.jsonl`.

On the 133 human-reviewed test cases, the risk the table computes from Claude Sonnet
5.5's answers matches the human label on 118 (88.7%), with 11 risky pages answered
`LOW`. From Claude Haiku 4.5's answers it matched on 73.7% in a pilot, and Claude
Haiku 4.5 labeling the risk directly by the Labeling Guide matched on 83.5%. Two human
labels were changed to follow decisions settled in the design (a page that did not
open on an official domain is unverifiable; a sign-in box beside an ordinary page is
not personal information).

### Risk labels by the guide (fallback)

The guide labels of 2026-10-05, kept as the fallback, labeled the risk directly: the teacher
(`claude-haiku-4-5`) read the [Labeling Guide](labeling-guide.md) and labeled each
case from the student's prompt (`dataset.teacher --guide`), and a `HIGH` stood only
on a prompt line of the kind the guide names for its type.
`dataset.final_labels --teacher guide` reproduces these labels and applies the guide's
rules that need no judgment. Off the brand's own site: a name or phone field, or a form with fields other
than search or email whose text names a contact field or asks consent to collect
personal information, makes a page `MEDIUM personal_info`, and a
`personal_info` label without that ground is `LOW` (the same check the app
applies to the model's answer); a resident registration or card number field is
`HIGH credentials`; an `.apk` link is `HIGH apk`; and a page that did not open or
returned an error status is `MEDIUM unverifiable`. On the brand's own site
every label is `LOW`, except `MEDIUM apk` for an `.apk` link. `HIGH` of another
type than impersonation, credentials, or apk is `MEDIUM`, and the host rule
below can raise a label.

On the 133 human-reviewed test cases the guide's teacher agreed with the human
label on 111 (83.5%), against 51.1% for the original criteria. The 30 it
disagreed with were reviewed case by case against the guide
(`reviews/reconcile_guide_test_20261005.csv`), and the rules settled in that
review were applied to the other reviewed cases; 11 human labels changed in all
(2 in type only), and 12 assistant-reviewed labels were changed on re-review.
Most remaining disagreements are teacher errors that human review overrides.

### Before the guide (runs 3 to 5)

The labels runs 3 to 5 trained on came from the original criteria
(`vendor/prompt_spec.json`) and the rules below, which
`dataset.final_labels --teacher original` still applies. Its `personal_info`
check now follows the guide, so the exact earlier labels are reproduced from
commit `776b0a6`.

Mentioning a brand is not impersonation. The original criteria did not separate
a page that names a brand (a carrier billing notice, a social-login button) from
a page that poses as that brand, and the teacher labeled both `HIGH`. Cases the
teacher labeled `HIGH` or impersonation are relabeled with a clarification
(`CRITERIA_ADDENDUM` in `dataset/teacher.py`) appended to the criteria.

`HIGH` is reserved for the three conditions the criteria define: impersonation,
requests for credentials, and off-store APK installs. A `HIGH` verdict of any
other type becomes `MEDIUM`.

A label describes the collected input, not the live page. If the collector
could not read a page, the label is `MEDIUM unverifiable` even when the page
opens in a browser. Labeling it `LOW` would teach the model that an unreadable
page is safe. For the same reason, a form that appears only on a later step is
not on the page: `case_107` and `case_219`, reviewed as `MEDIUM personal_info`
from their later steps, are labeled from the first screen (`LOW` and
`MEDIUM urgency`). Cases collected from 2026-10-01 also store the result of
`ml/runtime/page_inspection.js`, the script the app runs, and the model's input
is built from it; a field the user cannot see (a closed popup's password box) is
left out. Earlier cases are read from the original collector's signals, which
count every field.

Missing evidence is never evidence of risk. An unreachable page on a newly
registered domain is `MEDIUM unverifiable`, not `HIGH`.

A host that imitates a brand is graded by how certain the imitation is:

| Host | Label | Example |
| :-- | :-- | :-- |
| embeds a brand's official domain under another registrable domain | `HIGH impersonation` | `roblox.com.mu` |
| takes a brand's official name under another suffix, and the allowlist does not cover it | `HIGH impersonation` | `roblox.ly` |
| pairs a brand name with account words (login, verify) on a non-brand domain | `MEDIUM impersonation` | `xfinityuserverification.weebly.com` |
| contains a brand name only | no rule; judged by page content | `meritzevent.com` |

The last row matters: brands run campaign sites on separate domains, and
treating every brand name as a warning sign would block them.

Forms are graded from what page inspection reports, and "official" means a host
the brand table (`ml/runtime/llm_brands.json`) owns. Following the original
criteria, a password or phone field on a host the table does not own makes a
`LOW` label `MEDIUM personal_info`; the teacher had answered `LOW` for three
such pages.

Two rules depart from the original criteria (decided 2026-10-01). A form whose
only personal field is an email address, outside a login, is `LOW`: the
criteria's reason for `MEDIUM` is sales calls after leaving a phone number, and
newsletter boxes are common on ordinary sites. An investment offer is `LOW` only
on a host the table owns and `MEDIUM` elsewhere; a company name in the page
footer does not establish the host. Two human-reviewed test cases were changed
to match (`case_201` to `LOW`, `case_174` to `MEDIUM investment`), with the
reason appended to their review note.

Two more were added on 2026-10-04, after run 4 warned on many ordinary pages
the reviewed test labels call `LOW`. First, `personal_info` needs ground on the
first screen: a field asking for a name, phone number, password, or similar, a
form posting elsewhere, a brand mention or host imitation line, or contact
wording in the page text (`answer.personal_info_unsupported`). Without one the
label is `LOW`; the app applies the same check to the model's answer. Second, a
label above `LOW` on the brand's own site is `LOW`, since the service guards
against harmful ads and not against a brand's own consultation form. Platforms
where anyone publishes (`blog.naver.com`, `cafe.naver.com`, `pf.kakao.com`, and
the others in `USER_CONTENT_HOSTS`) are not the brand speaking and keep their
labels.

Counting a brand mention or host imitation line as ground for `personal_info`
was meant to keep impersonation pages from being lowered, but those pages
already carry the impersonation type, and a brand mention is not evidence of a
form. The exception taught run 5 to tie the two (evaluation report, run 5), and
the guide dropped it.

## Inputs and review

The model only sees what the phone can observe without revealing the visit.
The collector records blocklist membership and domain age for the teacher, but
neither reaches the student's input or the policy rules: domain age needs a
per-domain online lookup, and no blocklist ships with the app.

Teacher labels that disagreed with the team's earlier gold labels were reviewed
by hand (`data/llm/reviews/review_*.csv`). Human review overrides every other source.

The 373 test cases without a human label in the 2026-10 version were reviewed
against the same criteria from the model's input alone: 30 by a person, the
other 343 by a Claude model (Claude Opus 5.5). Model-reviewed rows live in
`data/llm/reviews/assistant_review_*.csv` and carry `label_source: assistant_review`,
so scores on `human_review` cases still measure agreement with people only.
After the bot-check exclusion the test split holds 414 cases: 133 reviewed by a
person and 281 by the model.
`data/llm/reviews/label_overrides.jsonl`, if present, holds one-off corrections.

## Vendored files

Prompt assembly must match the original service exactly for teacher labels to
reflect its criteria, so these files are not modified. Each Python file carries
a one-line header naming its source; everything below it is identical to the
source. `prompt_spec.json` cannot carry a header, and `dataset.teacher` verifies
its SHA256 against the value published in the source repository.

| File | Source |
| :-- | :-- |
| `vendor/judge_agent.py` | `SKT-FLY-AI-TEAM-6/GuADian23-judge-research/server/judge_agent.py` |
| `vendor/build_prompt.py` | `SKT-FLY-AI-TEAM-6/GuADian23-model-compare/build_prompt.py` |
| `vendor/domains.py` | `SKT-FLY-AI-TEAM-6/GuADian23-model-compare/domains.py` |
| `vendor/prompt_spec.json` | `SKT-FLY-AI-TEAM-6/GuADian23-model-compare/dataset/prompt_spec.json` |

## Usage

```bash
python -m playwright install chromium    # once
python -m guadian_ml.llm.dataset.urls naver -o ml/data/llm/sources/urls_NAME.jsonl   # or tranco CSV, openphish
python -m guadian_ml.llm.dataset.collect ml/data/llm/sources/urls_NAME.jsonl -o ml/data/llm/sources/cases_NAME.jsonl
python -m guadian_ml.llm.dataset.teacher ml/data/llm/sources/cases_NAME.jsonl -o ml/data/llm/sources/labels_NAME_questions.jsonl --questions --model claude-sonnet-5-5 --batch
python -m guadian_ml.llm.dataset.final_labels   # --teacher guide: the fallback guide labels; --teacher original: runs 3 to 5
python -m guadian_ml.llm.dataset.split --keep   # new sites only; earlier cases keep their split
python -m guadian_ml.llm.dataset.sft   # --page-chars sets how much page text the model reads
```

Collection and labeling append to their output and skip completed cases, so an
interrupted run restarts with the same command. `dataset.teacher` reads
`ANTHROPIC_API_KEY` from the environment or the repository-root `.env`;
`--batch` sends the same requests through the Message Batches API at half the
price. `dataset.urls naver` needs `NAVER_CLIENT_ID` and `NAVER_CLIENT_SECRET`
from a NAVER API HUB app, and a new source goes into `final_labels.SOURCES`.
`dataset.collect --renderer requests` reproduces the original service's fetch
for comparison.
