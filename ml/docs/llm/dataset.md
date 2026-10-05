# On-device LLM Dataset

Builds the training and evaluation data for the on-device Qwen judge (#11).

`dataset.urls` builds URL lists, and `dataset.collect` gathers, for each
landing-page URL, the evidence a judge needs: rendered page text, HTML signals,
blocklist membership, official-domain lookups, and domain age, plus the result
of the app's page inspection script for the model's input. `dataset.teacher` labels each case with a teacher model
(`claude-haiku-4-5`, the original GUADIAN server's judge) using the original
criteria. Human review and the labeling policy below then correct the teacher.
Claude is used only here, offline; the app does not call it.

## Pipeline

Modules are under `guadian_ml/llm/`; inputs and outputs are under
`data/llm/`, which is excluded from git because collected page text contains
third-party content and personal data.

| Module | Step |
| :-- | :-- |
| `dataset.urls` | Naver search, Tranco, or OpenPhish → `urls_*.jsonl`, skipping sites already listed |
| `dataset.collect` | URL list → `cases_*.jsonl` |
| `dataset.teacher` | `cases_*.jsonl` → `labels_*.jsonl` |
| `dataset.final_labels` | teacher labels + human review + assistant review + overrides + policy → `labels_final.jsonl` |
| `dataset.split` | `labels_final.jsonl` → `splits.jsonl` (train / validation / test, grouped by site) |
| `dataset.sft` | labels + cases → `sft_{train,validation,test}.jsonl`, compact chat examples |
| `contract.prompt` | the model's input: `PageObservation` → prompt, ported to `PromptBuilder.kt` |
| `contract.host_rules` | host-only brand-imitation grading, used by `dataset.final_labels` and the prompt |
| `contract.answer` | the app's reply reading, grounding, and personal_info check (`AnswerReader.kt`), for offline scoring and the labeling policy |
| `vendor/` | unmodified files from the original repositories |

## Labeling policy

The original criteria (`vendor/prompt_spec.json`) apply, with these rules on
top. `dataset.final_labels` applies them in code, so every case is labeled the same way.

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
form. The exception taught run 5 to tie the two (evaluation report, run 5), so it
is to be dropped from the label rule before run 6.

The model only sees what the phone can observe without revealing the visit.
The collector records blocklist membership and domain age for the teacher, but
neither reaches the student's input or the policy rules: domain age needs a
per-domain online lookup, and no blocklist ships with the app.

Teacher labels that disagreed with the team's earlier gold labels were reviewed
by hand (`data/llm/review_*.csv`). Human review overrides every other source.

The 373 test cases without a human label in the 2026-10 version were reviewed
against the same criteria from the model's input alone: 30 by a person, the
other 343 by a Claude model (Claude Opus 5.5). Model-reviewed rows live in
`data/llm/assistant_review_*.csv` and carry `label_source: assistant_review`,
so scores on `human_review` cases still measure agreement with people only.
`data/llm/label_overrides.jsonl`, if present, holds one-off corrections.

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
python -m guadian_ml.llm.dataset.urls naver -o ml/data/llm/urls_NAME.jsonl   # or tranco CSV, openphish
python -m guadian_ml.llm.dataset.collect ml/data/llm/urls_NAME.jsonl -o ml/data/llm/cases_NAME.jsonl
python -m guadian_ml.llm.dataset.teacher ml/data/llm/cases_NAME.jsonl -o ml/data/llm/labels_NAME.jsonl --batch
python -m guadian_ml.llm.dataset.final_labels
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
