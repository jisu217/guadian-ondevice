"""Write reports/llm/evaluation-report.md from the committed metric records.

Each `reports/llm/<name>_test.json` is written by `evaluation.gguf` (a GGUF model
run with llama.cpp) or copied from the training notebook (`*-colab`, the adapter
on the base model in Colab). The report only formats those numbers, so it can be
regenerated at any time and never disagrees with them.

Usage:
    python -m guadian_ml.llm.evaluation.report
"""

from __future__ import annotations

import json

from guadian_ml import paths

OUT = paths.LLM_REPORTS / "evaluation-report.md"

# Scored on the current test split (118 cases), so they can be compared row by row.
CURRENT = [
    ("run3-colab", "zero_shot", "Qwen3-1.7B, no fine-tuning (Colab, fp16)"),
    ("run3-colab", "fine_tuned", "run 3 adapter on the base (Colab, fp16)"),
    ("run3-f16", None, "run 3 GGUF f16"),
    ("run3-q8_0", None, "run 3 GGUF Q8_0"),
    ("run3-q5_k_m", None, "run 3 GGUF Q5_K_M"),
    ("run3-q4_k_m", None, "run 3 GGUF Q4_K_M"),
]
# The 2026-10 dataset's test split (420 reviewed cases, form kinds and shown fields
# in the prompt). Run 3 is scored on it too, so the two runs compare row by row.
RUN4 = [
    ("run4-colab", "zero_shot", "Qwen3-1.7B, no fine-tuning (Colab, fp16)"),
    ("run3-q4_k_m-ds2610", None, "run 3 GGUF Q4_K_M"),
    ("run4-colab", "fine_tuned", "run 4 adapter on the base (Colab, fp16)"),
    ("run4-f16", None, "run 4 GGUF f16"),
    ("run4-q8_0", None, "run 4 GGUF Q8_0"),
    ("run4-q5_k_m", None, "run 4 GGUF Q5_K_M"),
    ("run4-q4_k_m", None, "run 4 GGUF Q4_K_M"),
]
RUN4_Q4 = [("run3-q4_k_m-ds2610", "run 3 GGUF Q4_K_M"), ("run4-q4_k_m", "run 4 GGUF Q4_K_M")]
# Run 5 keeps run 4's settings and changes only training labels, on the same test split.
RUN5 = [
    ("run4-colab", "fine_tuned", "run 4 adapter on the base (Colab, fp16)"),
    ("run5-colab", "fine_tuned", "run 5 adapter on the base (Colab, fp16)"),
    ("run4-q4_k_m", None, "run 4 GGUF Q4_K_M"),
    ("run5-q4_k_m", None, "run 5 GGUF Q4_K_M"),
]
RUN5_Q4 = [("run4-q4_k_m", "run 4 GGUF Q4_K_M"), ("run5-q4_k_m", "run 5 GGUF Q4_K_M")]
# The same split, scored before the 2026-09-28 prompt changes.
RUN2 = [
    ("run2-colab", "fine_tuned", "run 2 adapter on the base (Colab, fp16)"),
    ("run2-f16", None, "run 2 GGUF f16"),
    ("run2-q8_0", None, "run 2 GGUF Q8_0"),
    ("run2-q5_k_m", None, "run 2 GGUF Q5_K_M"),
    ("run2-q4_k_m", None, "run 2 GGUF Q4_K_M"),
]
# Run 1 was trained and scored on an earlier dataset version; kept for the
# QLoRA decision below, not for comparison with the rows above.
RUN1 = [
    ("run1-colab", "fine_tuned", "run 1 QLoRA adapter on the 4-bit base (Colab)"),
    ("run1-f16", None, "run 1 merged into fp16, GGUF f16"),
]


# Saved answers of runs 3 to 5 rescored on the test split relabeled by the labeling guide.
# Run 6 answers questions; its risk comes from the verdict table, as in the app.
RUN6_APP = [
    ("run4-q4_k_m-guide", "run 4 GGUF Q4_K_M (risk model, relabeled split)"),
    ("run6-q4_k_m", "run 6 GGUF Q4_K_M"),
    ("run6-q8_0", "run 6 GGUF Q8_0"),
    ("teacher-questions", "teacher's answers (Claude Sonnet 5.5)"),
]

GUIDE_Q4 = [
    ("run3-q4_k_m-guide", "run 3 GGUF Q4_K_M"),
    ("run4-q4_k_m-guide", "run 4 GGUF Q4_K_M"),
    ("run5-q4_k_m-guide", "run 5 GGUF Q4_K_M"),
]


def load(name: str, section: str | None) -> dict:
    with open(paths.LLM_REPORTS / f"{name}_test.json", encoding="utf-8") as f:
        record = json.load(f)
    return record[section] if section else record


def pct(value: float) -> str:
    return f"{value:.1%}"


def table(rows: list[tuple[str, str | None, str]]) -> str:
    lines = ["| Model | Accuracy | Macro F1 | HIGH recall | Missed risk | Unparseable | Record |",
             "| :-- | --: | --: | --: | --: | --: | :-- |"]
    for name, section, label in rows:
        m = load(name, section)
        record = f"`{name}_test.json`" + (f" `{section}`" if section else "")
        lines.append(f"| {label} | {pct(m['accuracy'])} | {m['macro_f1']:.2f} | {pct(m['recall_HIGH'])} "
                     f"| {m['missed_risk']} | {m['unparseable']} | {record} |")
    return "\n".join(lines)


def subset_table(rows: list[tuple[str, str]]) -> str:
    """Model answers, the app's reading of them, and both on the human-reviewed cases."""
    lines = ["| Model | Reading | Cases | Accuracy | Macro F1 | LOW recall | Missed risk |",
             "| :-- | :-- | --: | --: | --: | --: | --: |"]
    for name, label in rows:
        r = load(name, None)
        for reading, m in (("model", r), ("app", r["app"]), ("model, human-reviewed", r["human_review"]),
                           ("app, human-reviewed", r["app"]["human_review"])):
            lines.append(f"| {label} | {reading} | {m['n']} | {pct(m['accuracy'])} | {m['macro_f1']:.2f} "
                         f"| {pct(m['recall_LOW'])} | {m['missed_risk']} |")
    return "\n".join(lines)


def source_table() -> str:
    """Misses on confirmed phishing and warnings on top sites, from the URL's source alone."""
    lines = ["| Judge | Phishing pages answered `LOW` (opened) | Phishing URLs answered `LOW` (all) "
             "| Top sites warned on (opened) | Top sites warned on (all) |",
             "| :-- | --: | --: | --: | --: |"]
    rows = [(name, ("app", "by_source"), label) for name, label in GUIDE_Q4]
    rows.append(("teacher-guide", ("by_source",), "teacher by the guide (Claude haiku)"))
    for name, keys, label in rows:
        m = load(name, None)
        for key in keys:
            m = m[key]
        p, t = m["openphish"], m["tranco"]
        reading = ", as the app reads it" if keys[0] == "app" else ""
        lines.append(f"| {label}{reading} | {p['opened_answered_low']} / {p['opened']} | {p['answered_low']} / {p['n']} "
                     f"| {t['opened_warned']} / {t['opened']} | {t['warned']} / {t['n']} |")
    return "\n".join(lines)


QUESTIONS = ["claims", "sign_in", "payment", "investment", "reward_pressure", "content_farm"]


def question_table(name: str) -> str:
    """Per-question agreement with the teacher's answers, from a question model's record."""
    m = load(name, "fine_tuned")
    lines = ["| Question | Accuracy | Recall of yes | Precision of yes |", "| :-- | --: | --: | --: |"]
    lines += [f"| `{q}` | {pct(m[q + '_accuracy'])} | {pct(m[q + '_recall'])} | {pct(m[q + '_precision'])} |"
              for q in QUESTIONS]
    return "\n".join(lines)


def question_app_table(rows: list[tuple[str, str]]) -> str:
    """The risk the app would show from each judge's answers, with the scores that need no label."""
    lines = ["| Judge | Human-reviewed accuracy | Accuracy | Missed risk | Opened phishing answered `LOW` "
             "| Opened top sites warned on |", "| :-- | --: | --: | --: | --: | --: |"]
    for name, label in rows:
        a = load(name, "app")
        p, t = a["by_source"]["openphish"], a["by_source"]["tranco"]
        lines.append(f"| {label} | {pct(a['human_review']['accuracy'])} | {pct(a['accuracy'])} | {a['missed_risk']} "
                     f"| {p['opened_answered_low']} / {p['opened']} | {t['opened_warned']} / {t['opened']} |")
    return "\n".join(lines)


def stability(run: str) -> str:
    """Agreement with the run's f16 file, from `evaluation.agreement`."""
    record = load(f"{run}-agreement", None)
    rows = ["| File | Same risk as f16 |", "| :-- | --: |"]
    for variant in record["variants"]:
        if variant != "f16":
            rows.append(f"| {variant.upper()} | {pct(record['pairwise'][f'f16|{variant}'])} |")
    return (f"All {len(record['variants'])} files give the same risk on "
            f"{pct(record['all_agree'])} of the {record['cases']} cases. "
            f"Agreement with f16, per quantized file:\n\n" + "\n".join(rows))


def render() -> str:
    q4 = load("run3-q4_k_m", None)
    return f"""# On-device LLM Evaluation

Generated by `python -m guadian_ml.llm.evaluation.report` from the JSON
records in this directory; edit those, not this file.

## Run 3: the risk model shipped before run 6

All rows below score the same {q4['n']}-case test split of the 2026-09 dataset
(`examples_sha256` in each record names the exact file), grouped by site so no
test site was seen in training. The app shipped this model until run 6. Missed risk counts
`MEDIUM` or `HIGH` cases answered `LOW`, the error that lets a harmful page
through. GGUF rows were run with `evaluation.gguf` on a Mac with the GPU, so
their latency fields do not describe a phone.

{table(CURRENT)}

Run 3 trains about four times as many optimizer steps as run 2 (5 epochs at an
effective batch of 8, with `HIGH` training cases repeated twice). Q4_K_M was the
shipping file: at 1.11 GB it is the smallest, and it scores at least as well as
the larger files. Its lead over Q5_K_M is 5 cases, within what one test split
can resolve. Reading the replies the way the app does (`app` in each GGUF
record) gives the same scores, with no malformed reply and no `HIGH` answer
lowered by the grounding check. On the {q4['human_review']['n']} hand-reviewed cases
Q4_K_M reaches {pct(q4['human_review']['accuracy'])} and still finds none of the
`HIGH` ones.

Missed risk did not fall: Q4_K_M misses {q4['missed_risk']} of the 70 risky cases,
against 20 for run 2's Q5_K_M. It catches six that run 2 missed, four of them
`HIGH` impersonation pages (two of which gained a host-imitation line in the
2026-09-28 prompt changes), and misses seven new ones, three of them `MEDIUM` pages collecting personal
details. Personal-information pages are now the largest group of misses (9 of
21). The model leans toward `LOW`: `LOW` recall is {pct(q4['recall_LOW'])} and
`MEDIUM` recall {pct(q4['recall_MEDIUM'])}.

## Run 4 on the 2026-10 dataset (not adopted)

Run 4 trains on the 2026-10 dataset: 1,465 training cases (381 before) from the
team's gold set, OpenPhish, Tranco Korean sites, and Naver search results, with
prompts that name form fields and leave out fields the user cannot see. Its test
split has {load("run4-q4_k_m", None)['n']} cases, all reviewed against the criteria from the
model's input ({load("run4-q4_k_m", None)['human_review']['n']} by a person, the rest by a Claude model; see
`ml/docs/llm/dataset.md`). Run 3 is scored on the same split.

{table(RUN4)}

Run 4 misses far fewer risky pages: {load("run4-q4_k_m", None)['missed_risk']} against
{load("run3-q4_k_m-ds2610", None)['missed_risk']} for run 3's Q4_K_M. Its 4-bit file keeps the f16
scores. But it warns on many ordinary pages (lower `LOW` recall), and on the
human-reviewed cases its accuracy falls below run 3's:

{subset_table(RUN4_Q4)}

Most of run 4's false alarms are `personal_info` answers on pages with no
personal field on the first screen. The reviewed test labels call such pages
`LOW`; the training labels, from the teacher, often did not. Two changes
followed on 2026-10-04: the app now lowers a `personal_info` answer the prompt
gives no ground for (`app` rows above), and the training labels follow the same
rule for run 5. The check was designed after reading run 4's test errors, so the
`app` rows overstate how well it generalizes.

Run 4: {stability("run4")}

## Run 5: aligned training labels (not adopted)

Run 5 keeps every run 4 setting and changes only training labels: 52 training
and 5 validation `personal_info` cases with no ground on the first screen, and 4
cases on brand sites, became `LOW`. The test split is unchanged.

{table(RUN5)}

{subset_table(RUN5_Q4)}

The aligned labels raised `LOW` recall and, read as the model answered, accuracy
on the human-reviewed cases, but did not pay off where it counts. Run 5 misses
more risky pages and finds fewer `HIGH` ones than run 4, and the app's check
lowers far fewer of its answers, so its app-read accuracy on the human-reviewed
cases falls below both run 4's and run 3's. It falls short of the bar set before
training: above run 3's {pct(load("run3-q4_k_m-ds2610", None)["app"]["human_review"]["accuracy"])} app-read accuracy on those cases, and at most 25
missed risky pages.

Comparing the fp16 answers case by case, run 5 fixes 28 run 4 errors and makes
24 new ones. Nine of the new ones are ordinary pages it calls `personal_info`;
most have a brand mention line and no form. The label rule keeps `personal_info`
when a brand mention or host imitation line is present, so after the change such
labels remained only on pages with a brand mention, and the model learned to
tie the two. The app's check keeps the same exception, so it does not catch
these answers. Five more are clear impersonation or credential pages answered
`MEDIUM` instead of `HIGH`; no `HIGH` label changed, and the cause is not yet
known.

## The original server judge on the same split

The original GUADIAN server judged with `claude-haiku-4-5` under the original
criteria; its answers are the teacher labels, so it can be scored on the same
{load("teacher-haiku", None)['n']} test cases at no cost (`teacher-haiku_test.json`).

| Judge | Accuracy | Macro F1 | `LOW` recall | Missed risk | Human-reviewed accuracy | Human-reviewed missed |
| :-- | --: | --: | --: | --: | --: | --: |
| original server (Claude haiku) | {pct(load("teacher-haiku", None)['accuracy'])} | {load("teacher-haiku", None)['macro_f1']:.2f} | {pct(load("teacher-haiku", None)['recall_LOW'])} | {load("teacher-haiku", None)['missed_risk']} | {pct(load("teacher-haiku", None)['human_review']['accuracy'])} | {load("teacher-haiku", None)['human_review']['missed_risk']} |
| run 3 GGUF Q4_K_M, as the app reads it | {pct(load("run3-q4_k_m-ds2610", None)['app']['accuracy'])} | {load("run3-q4_k_m-ds2610", None)['app']['macro_f1']:.2f} | {pct(load("run3-q4_k_m-ds2610", None)['app']['recall_LOW'])} | {load("run3-q4_k_m-ds2610", None)['app']['missed_risk']} | {pct(load("run3-q4_k_m-ds2610", None)['app']['human_review']['accuracy'])} | {load("run3-q4_k_m-ds2610", None)['app']['human_review']['missed_risk']} |
| run 4 GGUF Q4_K_M, as the app reads it | {pct(load("run4-q4_k_m", None)['app']['accuracy'])} | {load("run4-q4_k_m", None)['app']['macro_f1']:.2f} | {pct(load("run4-q4_k_m", None)['app']['recall_LOW'])} | {load("run4-q4_k_m", None)['app']['missed_risk']} | {pct(load("run4-q4_k_m", None)['app']['human_review']['accuracy'])} | {load("run4-q4_k_m", None)['app']['human_review']['missed_risk']} |
| run 5 GGUF Q4_K_M, as the app reads it | {pct(load("run5-q4_k_m", None)['app']['accuracy'])} | {load("run5-q4_k_m", None)['app']['macro_f1']:.2f} | {pct(load("run5-q4_k_m", None)['app']['recall_LOW'])} | {load("run5-q4_k_m", None)['app']['missed_risk']} | {pct(load("run5-q4_k_m", None)['app']['human_review']['accuracy'])} | {load("run5-q4_k_m", None)['app']['human_review']['missed_risk']} |

Only the human-reviewed column is a fair comparison. The assistant review
started from the teacher's answers and kept them where it agreed, so the server
matches 276 of those 287 labels, which inflates its overall accuracy. On the
human-reviewed cases the server warns on 45 of 76 ordinary pages and scores far
below the on-device models, while it misses the fewest risky pages. The two
follow different criteria: the reviewed labels apply the rules added since
(first screen only, brand sites, email-only forms), and the server applies the
original ones. How close the phone gets to the server therefore depends on
which criteria are the target; the next section restates the scores under the
labeling guide that replaced both.

## The relabeled test split (labeling guide)

On 2026-10-05 the labels were rewritten with the labeling guide
(`ml/docs/llm/labeling-guide.md`): the teacher relabeled every case from the
student's prompt, 38 pages a bot defense served to the collector were left out
(6 of them test cases), and the reviewed test labels were reconciled with the
guide, changing 11 human and 12 assistant-reviewed labels. The app's
`personal_info` check changed with it: it now needs a personal field or a form
worded to collect contact details, and no longer keeps answers on a brand
mention. Earlier runs' saved answers are rescored here on the
{load("run3-q4_k_m-guide", None)['n']} relabeled test cases, including the 23 labels a blind review changed on 2026-10-09
(run 6 below); the sections above keep their original split.

{subset_table(GUIDE_Q4)}

| Judge | Accuracy | Macro F1 | `LOW` recall | Missed risk | Human-reviewed accuracy | Human-reviewed missed |
| :-- | --: | --: | --: | --: | --: | --: |
| teacher by the guide (Claude haiku) | {pct(load("teacher-guide", None)['accuracy'])} | {load("teacher-guide", None)['macro_f1']:.2f} | {pct(load("teacher-guide", None)['recall_LOW'])} | {load("teacher-guide", None)['missed_risk']} | {pct(load("teacher-guide", None)['human_review']['accuracy'])} | {load("teacher-guide", None)['human_review']['missed_risk']} |

The guide's teacher agrees with people on far more of the human-reviewed cases
than the original server did ({pct(load("teacher-haiku", None)['human_review']['accuracy'])}), so the training labels and the test labels now
follow the same criteria. Its overall figure is inflated the same way as before:
the reviewed labels were reconciled with its answers. On this split the bar for
run 6 is run 3's {pct(load("run3-q4_k_m-guide", None)["app"]["human_review"]["accuracy"])} app-read accuracy on the human-reviewed cases, and at most 25
missed risky pages. Run 4 as the app reads it misses few risky pages but, with the
reviewed labels, falls just below run 3 on the human-reviewed cases, and the check it
relies on was designed after reading this split's errors.

## Scores that do not depend on our labels

The labeling guide and the reviewed test labels were written by the same
people, so the scores above measure how well a model follows that guide. Two
scores need no label at all, only where a URL came from. OpenPhish URLs were
reported and confirmed as phishing by a third party, so a `LOW` answer on one
is a miss whatever the guide says. Tranco URLs are the most visited sites, so a
warning on one that opened is a false alarm by the source alone. Pages that did
not open are counted apart: the guide warns on those as unverifiable.

{source_table()}

Most phishing URLs no longer opened when collected, so the opened pages are
the informative ones. On those, run 4 answers `LOW` on far fewer than run 3,
and on opened top sites every model warns on only a few, so the gap between
runs 3 and 4 seen under our labels is confirmed by these label-free scores.

## Run 6: the first question model

Run 6 is the first model of the [Verdict Design](../../../docs/design.md): instead of
grading the risk, it answers the six questions of the
[Question Guide](../../docs/llm/question-guide.md) as one JSON line, and the verdict
table turns its answers and the page's facts into the risk. Its training targets are
Claude Sonnet 5.5's answers (`ml/docs/llm/dataset.md`); the training settings are run
4's. The prompt and its system line are unchanged, so the app sends the same input as
to run 3.

Agreement with the teacher's answers on the {load("run6-colab", "fine_tuned")['n']} test cases, scored in Colab on the
fp16 base (`run6-colab_test.json`): every reply parses (the untuned model parsed none),
and all six answers match on {pct(load("run6-colab", "fine_tuned")['all_correct'])} of the cases.

{question_table("run6-colab")}

No test case has a yes answer for `payment` or `reward_pressure`, so their accuracy
says nothing about finding them; the model never answers yes to `content_farm`.

The risk the app would show, against the reviewed test labels:

{question_app_table(RUN6_APP)}

A person reviewed the labels behind run 6's misses before this table was final. Of
Q4_K_M's 34 misses at first, 21 had labels a Claude model had written; the project
owner relabeled those 21, and the 2 false alarms with such labels, from the model
input alone with the earlier label hidden (`reviews/review_blind_run6_20261009.csv`).
The review agreed with the earlier label on 12 of the 23; 9 pages the earlier labels
called risky (news articles, information posts, ordinary shops) are `LOW`, which run 6
had answered. The scores above use the reviewed labels: {load("run6-q4_k_m", "app")['human_review']['n']} test cases are now
human-reviewed.

Run 6 clears the bar: above run 3's {pct(load("run3-q4_k_m-guide", None)["app"]["human_review"]["accuracy"])} on the human-reviewed cases, and at most 25 missed
risky pages ({load("run6-q4_k_m", "app")['missed_risk']} for Q4_K_M, {load("run6-q8_0", "app")['missed_risk']} for Q8_0), with almost no warning on top sites.
It ships as Q8_0; its emulator timing against Q4_K_M is in the
[Technical Report](../../../docs/technical-report.md).
Run 4 misses fewer risky pages ({load("run4-q4_k_m-guide", "app")['missed_risk']}) but raises 18 false alarms to Q4_K_M's 4 and
falls below run 3 on the human-reviewed cases, and its figures rest on an app check
designed after reading this split. Q4_K_M loses part of the claims: the teacher's
claims it recalls fall from {pct(load("run6-q8_0", None)['claims_recall'])} (Q8_0, as in fp16) to {pct(load("run6-q4_k_m", None)['claims_recall'])}, and two impersonation
pages of listed brands it misses (TokenPocket, imToken) are found by Q8_0.

What run 6 still misses mostly comes from the table and the labels rather than the
model: the teacher's own answers miss {load("teacher-questions", "app")['missed_risk']} risky pages through the same table. Its
weakest point is phishing that opened: {load("run6-q4_k_m", "app")['by_source']['openphish']['opened_answered_low']} of {load("run6-q4_k_m", "app")['by_source']['openphish']['opened']} such pages are answered `LOW`, against
{load("run4-q4_k_m-guide", "app")['by_source']['openphish']['opened_answered_low']} for run 4. Before the review, by cause, Q4_K_M's misses were content farms the model
does not recognize, nearly empty pages and error screens, payment, urgency, or
investment calls, impersonation of listed brands lost to Q4, claims of brands outside
the table without a sign-in screen, and a few others. The review judged every nearly
empty page and error screen as unverifiable, which the table cannot give a page that
opened. Simulated on Q4_K_M's answers against the earlier labels, treating a page under
100 characters as unverifiable cut misses from 34 to 30 for 3 more false alarms, and
adding brand claims outside the table as `MEDIUM` cut them to 32 for 1 more.

## Stability across quantization

Run 3: {stability("run3")}

Run 2: {stability("run2")}

Run 3's quantized files keep far more of the f16 model's answers; Q4_K_M went
from changing about one answer in three to about one in twelve. Misses are
learned rather than quantization noise: run 2's f16 file answers `LOW` on 17
of the 20 risky cases its Q5_K_M misses.

## Run 2

{table(RUN2)}

Run 2 took 72 optimizer steps (3 epochs at an effective batch of 16). It was
scored before the 2026-09-28 prompt changes (brand names under another suffix,
added brands, and global brands in the official-domain line), which changed 8
of the 118 test prompts.

## Run 1: why LoRA instead of QLoRA

{table(RUN1)}

Run 1 trained a QLoRA adapter on the 4-bit base. Merged into the fp16 weights
for GGUF export, it scored about six points lower than in training, so later
runs train plain LoRA on the fp16 base, the weights the export starts from.
Run 1 used an earlier dataset version, so these rows do not compare with the
ones above.
"""


def main() -> None:
    OUT.write_text(render(), encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
