"""Turn labeled cases into compact chat examples for fine-tuning the on-device model.

A 1.7B model on a phone reads long instructions far too slowly, so the student gets:

    system     one fixed line; what to answer is learned in training, not read at run time
    user       the observations, one per line, plus the start of the page text; about
               500 tokens at most
    assistant  the answers to the six questions of docs/llm/question-guide.md as compact
               JSON: {"claims", "evidence", "sign_in", "payment", "investment",
               "reward_pressure", "content_farm"}, when the labels carry the teacher's
               answers (final_labels --teacher questions); otherwise the risk labels of
               runs 3 to 6, {"risk", "type", "evidence"}

The system line is unchanged from the risk-grading runs, so the app sends the same input
to either kind of model. A question example also carries `risk`, its final label, and
`facts`, the verdict table's other facts, and `landing`, the landing host its claim is
checked against, so a model's answers can be scored the way the
app reads them.

Every user line is something the app can compute on the phone from Page Content
Inspection, the bundled official-domain table, and host_rules.py. The collector
also records blocklist membership and domain age, but they are left out: domain
age needs a per-domain online lookup that would reveal the visit, and no
blocklist ships with the app. The app's prompt builder must produce the same
lines in the same order.

For a question example, `evidence` is the prompt line that shows the claim, empty
without one. For a risk example, `evidence` is filled only for HIGH, the one level the on-device grounding check
verifies. It is the user-message line that supports the type (host imitation or
brand mismatch for impersonation, the input-field line for credentials, the APK
line for apk), so it always appears verbatim in the input. MEDIUM and LOW answers
leave it empty, which also keeps the output short.

Usage:
    python -m guadian_ml.llm.dataset.sft [--page-chars 500]
"""

from __future__ import annotations

import argparse
import collections
import json
import sys

from guadian_ml import paths
from guadian_ml.llm.contract.prompt import SYSTEM, Brands, ascii_host, observation_from_case, user_message
from guadian_ml.llm.dataset.final_labels import SOURCES


def read_jsonl(path: str) -> list[dict]:
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


EVIDENCE_LINES = {
    # The title line is the fallback: it is what the page claims to be.
    "impersonation": ("주소 흉내:", "브랜드 언급:", "제목:"),
    "credentials": ("입력 칸:", "브랜드 언급:", "제목:"),
    "apk": ("APK 링크:",),
}


def answer(label: dict, user: str) -> dict:
    evidence = ""
    if label["risk"] == "HIGH":
        lines = user.split("\n")
        evidence = next((line for prefix in EVIDENCE_LINES.get(label["type"], ())
                         for line in lines if line.startswith(prefix)), "")
    return {"risk": label["risk"], "type": label["type"], "evidence": evidence}


ANSWER_KEYS = ("claims", "evidence", "sign_in", "payment", "investment", "reward_pressure", "content_farm")


def question_answer(label: dict, user: str) -> dict | None:
    """The teacher's answers as the training target, or None when a claim's evidence line is not in
    `user` (another --page-chars than the teacher read with)."""
    target = {key: label["answers"][key] for key in ANSWER_KEYS}
    # Compared as the app compares: each line with its surrounding whitespace removed.
    if target["claims"] and target["evidence"] not in [line.strip() for line in user.split("\n")]:
        return None
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description="Build compact chat examples for fine-tuning.")
    parser.add_argument("--page-chars", type=int, default=500)
    args = parser.parse_args()

    labels = {r["case_id"]: r for r in read_jsonl(paths.LLM_FINAL / "labels_final.jsonl")}
    splits = {r["case_id"]: r["split"] for r in read_jsonl(paths.LLM_FINAL / "splits.jsonl")}
    cases, teachers = {}, {}
    for source in SOURCES:
        cases.update({c["case_id"]: c for c in read_jsonl(paths.LLM_SOURCES / f"cases_{source}.jsonl")})
        for suffix in ("", "_addendum"):   # addendum labels replace the first pass where present
            path = paths.LLM_SOURCES / f"labels_{source}{suffix}.jsonl"
            if path.exists():
                teachers.update({r["case_id"]: r["verdict"] for r in read_jsonl(path) if "verdict" in r})

    tokenizer = None
    if (paths.TOKENIZER / "tokenizer.json").exists():
        from tokenizers import Tokenizer
        tokenizer = Tokenizer.from_file(str(paths.TOKENIZER / "tokenizer.json"))

    outputs = {split: [] for split in ("train", "validation", "test")}
    brands = Brands.load()
    lengths, kept_evidence, missing = [], collections.Counter(), []
    for case_id, label in labels.items():
        user = user_message(observation_from_case(cases[case_id]), brands, args.page_chars)
        if "answers" in label:
            target = question_answer(label, user)
            if target is None:
                sys.exit(f"{case_id}: the claim's evidence line is not in the prompt; build with the teacher's page chars")
            if target["claims"]:
                kept_evidence[True] += 1
        else:
            target = answer(label, user)
            if target["risk"] == "HIGH":
                kept_evidence[bool(target["evidence"])] += 1
                if not target["evidence"]:
                    missing.append(case_id)
        example = {
            "case_id": case_id,
            "label_source": label["label_source"],
            "messages": [
                {"role": "system", "content": SYSTEM},
                {"role": "user", "content": user},
                {"role": "assistant", "content": json.dumps(target, ensure_ascii=False)},
            ],
        }
        if "answers" in label:
            # What scoring needs to turn a model's answers into the app's risk, and the risk to compare.
            obs = observation_from_case(cases[case_id])
            example.update(risk=label["risk"], facts=label["facts"],
                           landing=ascii_host((obs.final_url or obs.url) if obs.loaded else obs.url))
        outputs[splits[case_id]].append(example)
        if tokenizer:
            lengths.append(len(tokenizer.encode(SYSTEM + "\n" + user).ids))

    # Every collected observation with its prompt, for a stronger local-only parity
    # check; it holds page text, so it stays in the git-ignored data directory.
    with open(paths.LLM_FINAL / "llm_prompt_cases_full.jsonl", "w", encoding="utf-8") as f:
        for case_id in labels:
            obs = observation_from_case(cases[case_id])
            f.write(json.dumps({"observation": obs.to_json(), "prompt": user_message(obs, brands)},
                               ensure_ascii=False) + "\n")

    for split, rows in outputs.items():
        path = paths.LLM_FINAL / f"sft_{split}.jsonl"
        with open(path, "w", encoding="utf-8") as f:
            f.writelines(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
        print(f"{split:<10} {len(rows):>4} -> {path}")
    if lengths:
        lengths.sort()
        pick = lambda q: lengths[min(len(lengths) - 1, int(q * len(lengths)))]
        print(f"prompt tokens: median {pick(0.5)}, p90 {pick(0.9)}, max {lengths[-1]}")
    if any("answers" in label for label in labels.values()):
        print(f"answers with a claim and its evidence line: {kept_evidence[True]}")
    else:
        print(f"HIGH answers with evidence: {kept_evidence[True]} / {sum(kept_evidence.values())}"
              + (f"; missing: {missing}" if missing else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
