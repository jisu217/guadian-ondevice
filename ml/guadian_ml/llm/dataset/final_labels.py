"""Merge teacher labels, labeling policy, and human review into one label per case.

Precedence, highest first:
    1. Human review   data/llm/review_*.csv (final_risk, final_type)
    2. Assistant review data/llm/assistant_review_*.csv, the same columns filled by a
                      Claude model reading the same input; kept apart from human review
                      so evaluation on human labels stays human
    3. Manual override data/llm/label_overrides.jsonl, for one-off corrections
    4. Policy         README "Labeling policy", applied in code:
                        - HIGH types: HIGH is kept only for impersonation, credentials, and apk,
                          the three HIGH conditions in the criteria; other HIGH becomes MEDIUM
                        - no evidence: a HIGH verdict on a page that returned no content
                          (fetch error or HTTP 4xx/5xx) becomes MEDIUM unverifiable,
                          unless the host rule says HIGH. Blocklist membership does not
                          count: the student model never sees it
                        - forms, from what page inspection reports (asks) and the brand table:
                          a password form on a host the table does not own, or a phone field
                          on such a host, is at least MEDIUM personal_info; a MEDIUM
                          personal_info whose only personal field is an email address is LOW
                        - investment: MEDIUM investment on a host the brand table owns is LOW
                        - host rule (host_rules.grade_case, clicked URL and landing page):
                          raises the label to the host verdict when that is higher; never lowers it
    5. Teacher        data/llm/labels_*_addendum.jsonl when present (criteria plus llm.dataset.teacher CRITERIA_ADDENDUM),
                      else data/llm/labels_*.jsonl; both after the evidence check

Every output row records which step produced it, so a later audit can separate
human-confirmed labels from teacher and policy labels.

Usage:
    python -m guadian_ml.llm.dataset.final_labels
"""

from __future__ import annotations

import collections
import csv
import glob
import json
import os
import sys

from guadian_ml import paths
from guadian_ml.llm.contract.host_rules import grade_case
from guadian_ml.llm.contract.prompt import Brands, ascii_host, observation_from_case

DATA_DIR = paths.LLM_DATA
SOURCES = ("gold251_js", "openphish_20260926_js", "openphish_20261001", "tranco_kr_20261001", "naver_20261002")
OVERRIDES = os.path.join(DATA_DIR, "label_overrides.jsonl")
OUT = os.path.join(DATA_DIR, "labels_final.jsonl")
RISK_RANK = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
HIGH_TYPES = {"impersonation", "credentials", "apk"}


def read_jsonl(path: str) -> list[dict]:
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def tool_results(case: dict) -> dict[str, list[dict]]:
    results: dict[str, list[dict]] = collections.defaultdict(list)
    for item in case["input"]["tool_outputs"]:
        results[item["tool"]].append(json.loads(item["output"]))
    return results


def apply_policy(case: dict, teacher: dict, brands: Brands) -> tuple[str, str, str, str]:
    """(risk, type, label_source, note) after the labeling policy."""
    tools = tool_results(case)
    fetch = tools["fetch_page"][0]
    no_content = "error" in fetch or (fetch.get("status") or 0) >= 400
    final_url = None if "error" in fetch else fetch.get("final_url")
    host = grade_case(case["url"], final_url)
    obs = observation_from_case(case)
    asks = {a for form in obs.forms for a in form.asks}
    official = obs.loaded and brands.official_brand_of(ascii_host(obs.final_url or obs.url)) is not None

    risk, type_, source, note = teacher["risk"], teacher["type"], "teacher", ""
    if risk == "HIGH" and type_ not in HIGH_TYPES:
        risk, source = "MEDIUM", "policy:high_type"
        note = f"HIGH is reserved for {sorted(HIGH_TYPES)}; {type_} is at most MEDIUM"
    if risk == "HIGH" and no_content and not (host and host.risk == "HIGH"):
        risk, type_, source = "MEDIUM", "unverifiable", "policy:no_evidence"
        note = "page returned no content; missing evidence cannot support HIGH"
    if risk == "LOW" and not official and asks & {"password", "phone"}:
        risk, type_, source = "MEDIUM", "personal_info", "policy:form"
        note = f"{', '.join(sorted(asks & {'password', 'phone'}))} field on a host the brand table does not own"
    if (risk, type_) == ("MEDIUM", "personal_info") and "email" in asks and asks <= {"email", "search"}:
        risk, type_, source = "LOW", "none", "policy:email_only"
        note = "the only personal field is an email address"
    if (risk, type_) == ("MEDIUM", "investment") and official:
        risk, type_, source = "LOW", "none", "policy:official_investment"
        note = "investment offer on a host the brand table owns"
    if host and RISK_RANK[host.risk] > RISK_RANK[risk]:
        risk, type_, source, note = host.risk, host.type, "policy:host", host.reason
    return risk, type_, source, note


def read_reviews(pattern: str) -> dict[str, dict]:
    """Rows with a final risk and type from review CSVs matching pattern; blank rows are not reviewed yet."""
    reviewed: dict[str, dict] = {}
    for path in sorted(glob.glob(os.path.join(DATA_DIR, pattern))):
        with open(path, encoding="utf-8-sig") as f:
            for row in csv.DictReader(f):
                if row.get("final_risk") in RISK_RANK and row.get("final_type"):
                    reviewed[row["case_id"]] = row
    return reviewed


def main() -> int:
    reviewed = read_reviews("review_*.csv")
    assistant = read_reviews("assistant_review_*.csv")
    overrides = {row["case_id"]: row for row in read_jsonl(OVERRIDES)} if os.path.exists(OVERRIDES) else {}

    brands = Brands.load()
    out_rows = []
    for source in SOURCES:
        cases = {c["case_id"]: c for c in read_jsonl(os.path.join(DATA_DIR, f"cases_{source}.jsonl"))}
        labels = {r["case_id"]: r for r in read_jsonl(os.path.join(DATA_DIR, f"labels_{source}.jsonl"))}
        addendum_path = os.path.join(DATA_DIR, f"labels_{source}_addendum.jsonl")
        if os.path.exists(addendum_path):
            labels.update({r["case_id"]: r for r in read_jsonl(addendum_path) if "verdict" in r})
        if cases.keys() != labels.keys():
            sys.exit(f"{source}: cases and labels cover different case IDs")
        for case_id, label in labels.items():
            if "verdict" not in label:
                sys.exit(f"{case_id}: no teacher verdict ({label.get('error')})")
            teacher = label["verdict"]
            row = {"case_id": case_id, "source": source, "url": cases[case_id]["url"],
                   "teacher_risk": teacher["risk"], "teacher_type": teacher["type"]}
            if case_id in reviewed:
                r = reviewed[case_id]
                row.update(risk=r["final_risk"], type=r["final_type"], label_source="human_review", note=r["note"])
            elif case_id in assistant:
                r = assistant[case_id]
                row.update(risk=r["final_risk"], type=r["final_type"], label_source="assistant_review", note=r["note"])
            elif case_id in overrides:
                o = overrides[case_id]
                row.update(risk=o["risk"], type=o["type"], label_source="override", note=o["reason"])
            else:
                risk, type_, label_source, note = apply_policy(cases[case_id], teacher, brands)
                row.update(risk=risk, type=type_, label_source=label_source, note=note)
            out_rows.append(row)

    known = {r["case_id"] for r in out_rows}
    unknown = (reviewed.keys() | assistant.keys() | overrides.keys()) - known
    if unknown:
        sys.exit(f"review or override rows without a case: {sorted(unknown)}")

    with open(OUT, "w", encoding="utf-8") as f:
        f.writelines(json.dumps(r, ensure_ascii=False) + "\n" for r in out_rows)

    print(f"wrote {len(out_rows)} rows to {OUT}")
    for source in SOURCES:
        rows = [r for r in out_rows if r["source"] == source]
        print(f"  {source}: risk {dict(collections.Counter(r['risk'] for r in rows))}, "
              f"label_source {dict(collections.Counter(r['label_source'] for r in rows))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
