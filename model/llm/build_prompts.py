"""Turn labeled cases into compact chat examples for fine-tuning the on-device model.

The teacher saw the original prompt: about 4,700 tokens of criteria plus raw tool
JSON. A 1.7B model on a phone reads that far too slowly, so the student gets:

    system     one fixed line; the criteria are learned in training, not read at run time
    user       the observations that decide a verdict, one per line, plus the start of
               the page text; about 500 tokens at most
    assistant  {"risk", "type", "evidence"} as compact JSON

Every user line is something the app can compute on the phone from Page Content
Inspection, the bundled official-domain table, and host_rules.py. The collector
also records blocklist membership and domain age, but they are left out: domain
age needs a per-domain online lookup that would reveal the visit, and no
blocklist ships with the app. The app's prompt builder must produce the same
lines in the same order.

`evidence` is filled only for HIGH, the one level the on-device grounding check
verifies. It is the user-message line that supports the type (host imitation or
brand mismatch for impersonation, the input-field line for credentials, the APK
line for apk), so it always appears verbatim in the input. MEDIUM and LOW answers
leave it empty, which also keeps the output short.

Usage:
    python model/llm/build_prompts.py [--page-chars 500]
"""

from __future__ import annotations

import argparse
import collections
import json
import os
import sys
from urllib.parse import urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from host_rules import global_brand_mismatches, grade_case  # noqa: E402

DATA_DIR = os.path.join(HERE, "data")
SOURCES = ("gold251_js", "openphish_20260926_js")
TOKENIZER = os.path.join(DATA_DIR, "tokenizer", "tokenizer.json")
SYSTEM = "광고 도착 페이지의 위험도를 판정한다. JSON 한 줄로만 답한다."
MAX_URL_CHARS = 200


def read_jsonl(path: str) -> list[dict]:
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def tools_of(case: dict) -> dict[str, list[dict]]:
    tools: dict[str, list[dict]] = collections.defaultdict(list)
    for item in case["input"]["tool_outputs"]:
        tools[item["tool"]].append((item["args"], json.loads(item["output"])))
    return tools


def ascii_host(url: str) -> str:
    host = (urlsplit(url).hostname or "").lower()
    try:
        return host.encode("idna").decode("ascii")
    except UnicodeError:
        return host


def user_message(case: dict, page_chars: int) -> str:
    tools = tools_of(case)
    url = case["url"]
    host = ascii_host(url)
    fetch = tools["fetch_page"][0][1]
    lines = [f"주소: {url[:MAX_URL_CHARS]}"]

    if "error" in fetch:
        lines.append("페이지: 열리지 않음")
    else:
        final_host = ascii_host(fetch.get("final_url") or url)
        hops = max(0, len(fetch.get("chain") or []) - 1)
        if final_host and final_host != host:
            lines.append(f"도착: {final_host} (이동 {hops}회)")
        lines.append(f"페이지: 상태 {fetch.get('status')}, 본문 {fetch.get('text_chars', 0)}자")

    final_url = url if "error" in fetch else (fetch.get("final_url") or url)
    signals = tools["page_signals"][0][1] if tools["page_signals"] else {}
    if signals.get("title"):
        lines.append(f"제목: {signals['title'][:80]}")

    for args, result in tools["official_domain_of"]:
        if "domain" in result:   # asked by domain: is the destination an official domain?
            lines.append(f"공식 도메인: {result['domain']} = {result['brand']}" if result.get("known")
                         else f"공식 도메인: {result['domain']} 표에 없음")
    named = [(m["brand"], m["official"][0]) for m in signals.get("brand_domain_mismatch") or []]
    page_words = (signals.get("title") or "") + " " + (fetch.get("text") or "")
    named += global_brand_mismatches(page_words, ascii_host(final_url))
    if named:
        lines.append("브랜드 언급: " + ", ".join(f"{b}(공식 {d})" for b, d in named[:4])
                     + f" / 실제 {ascii_host(final_url)}")

    asks = sorted({a for form in signals.get("forms") or [] for a in form.get("asks") or []})
    if signals.get("forms"):
        external = any(form.get("external_action") for form in signals["forms"])
        lines.append(f"입력 칸: {', '.join(asks) if asks else '일반'}" + (" (외부로 전송)" if external else ""))
    if signals.get("apk_links"):
        lines.append(f"APK 링크: {signals['apk_links'][0][:100]}")

    host_verdict = grade_case(url, final_url)
    if host_verdict:
        lines.append(f"주소 흉내: {host_verdict.reason}")

    text = (fetch.get("text") or "")[:page_chars]
    if text:
        lines.append(f"본문: {text}")
    return "\n".join(lines)


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


def main() -> int:
    parser = argparse.ArgumentParser(description="Build compact chat examples for fine-tuning.")
    parser.add_argument("--page-chars", type=int, default=500)
    args = parser.parse_args()

    labels = {r["case_id"]: r for r in read_jsonl(os.path.join(DATA_DIR, "labels_final.jsonl"))}
    splits = {r["case_id"]: r["split"] for r in read_jsonl(os.path.join(DATA_DIR, "splits.jsonl"))}
    cases, teachers = {}, {}
    for source in SOURCES:
        cases.update({c["case_id"]: c for c in read_jsonl(os.path.join(DATA_DIR, f"cases_{source}.jsonl"))})
        for suffix in ("", "_addendum"):   # addendum labels replace the first pass where present
            path = os.path.join(DATA_DIR, f"labels_{source}{suffix}.jsonl")
            if os.path.exists(path):
                teachers.update({r["case_id"]: r["verdict"] for r in read_jsonl(path) if "verdict" in r})

    tokenizer = None
    if os.path.exists(TOKENIZER):
        from tokenizers import Tokenizer
        tokenizer = Tokenizer.from_file(TOKENIZER)

    outputs = {split: [] for split in ("train", "validation", "test")}
    lengths, kept_evidence, missing = [], collections.Counter(), []
    for case_id, label in labels.items():
        user = user_message(cases[case_id], args.page_chars)
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
        outputs[splits[case_id]].append(example)
        if tokenizer:
            lengths.append(len(tokenizer.encode(SYSTEM + "\n" + user).ids))

    for split, rows in outputs.items():
        path = os.path.join(DATA_DIR, f"sft_{split}.jsonl")
        with open(path, "w", encoding="utf-8") as f:
            f.writelines(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
        print(f"{split:<10} {len(rows):>4} -> {path}")
    if lengths:
        lengths.sort()
        pick = lambda q: lengths[min(len(lengths) - 1, int(q * len(lengths)))]
        print(f"prompt tokens: median {pick(0.5)}, p90 {pick(0.9)}, max {lengths[-1]}")
    print(f"HIGH answers with evidence: {kept_evidence[True]} / {sum(kept_evidence.values())}"
          + (f"; missing: {missing}" if missing else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
