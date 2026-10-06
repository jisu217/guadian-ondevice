"""Merge teacher labels, labeling policy, and human review into one label per case.

Precedence, highest first:
    1. Human review   data/llm/reviews/review_*.csv (final_risk, final_type)
    2. Assistant review data/llm/reviews/assistant_review_*.csv, the same columns filled by a
                      Claude model reading the same input; kept apart from human review
                      so evaluation on human labels stays human
    3. Manual override data/llm/reviews/label_overrides.jsonl, for one-off corrections
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
                          personal_info whose only personal field is an email address is LOW;
                          a MEDIUM personal_info the prompt gives no ground for
                          (answer.personal_info_unsupported, the app's own check) is LOW
                        - official: a label above LOW on a host the brand table owns is LOW,
                          except on platforms where anyone publishes (USER_CONTENT_HOSTS)
                        - host rule (host_rules.grade_case, clicked URL and landing page):
                          raises the label to the host verdict when that is higher; never lowers it
    5. Teacher        data/llm/sources/labels_*_addendum.jsonl when present (criteria plus llm.dataset.teacher CRITERIA_ADDENDUM),
                      else data/llm/sources/labels_*.jsonl; both after the evidence check

--teacher guide reads data/llm/sources/labels_*_guide.jsonl instead, labels the teacher
gave by docs/llm/labeling-guide.md from the student's prompt. The guide already holds
the rules the policy patches in, so only its checks that need no judgment run in
code (apply_guide_checks), each off the brand's own site unless noted: HIGH types;
personal_info follows the field-and-form rule exactly (guide rule 5,
answer.personal_info_unsupported, the app's own check): a LOW with that ground becomes
MEDIUM personal_info, and a MEDIUM personal_info without it becomes LOW; a resident
registration or card number field is HIGH credentials (rule 4); an .apk link is
HIGH apk (rule 2), and MEDIUM apk on the brand's own site; a LOW on a page that did not
open or returned an error status is MEDIUM unverifiable (rule 1); the brand's own site is
otherwise LOW, loaded or not (rules 0 and 1); and the host rule.

--teacher questions (the default) reads data/llm/sources/labels_*_questions.jsonl, the
teacher's answers to the six questions of docs/llm/question-guide.md (docs/design.md,
section 8). The verdict table (contract/verdict_table.py) turns those answers and the
facts code computes (contract/facts.py, the app's own signals) into the risk, and no
other rule runs. Each row also keeps the answers, the training target, and the facts
without them, so a model's answers can be scored the way the app reads them.

Every output row records which step produced it, so a later audit can separate
human-confirmed labels from teacher and policy labels.

Pages a bot defense served to the collector (dataset.bot_check) are left out of the
labels, and so of training and evaluation, and listed in final/excluded.jsonl.

Usage:
    python -m guadian_ml.llm.dataset.final_labels [--teacher questions|guide|original]
"""

from __future__ import annotations

import argparse
import collections
import csv
import glob
import json
import os
import sys

from guadian_ml import paths
from guadian_ml.llm.contract.answer import personal_info_unsupported
from guadian_ml.llm.contract.host_rules import USER_CONTENT_HOSTS, grade_case
from guadian_ml.llm.contract.prompt import Brands, ascii_host, observation_from_case, user_message
from guadian_ml.llm.contract import facts as verdict_facts
from guadian_ml.llm.contract.verdict_table import VerdictTable
from guadian_ml.llm.dataset.bot_check import bot_check

SOURCES_DIR = paths.LLM_SOURCES
REVIEWS_DIR = paths.LLM_REVIEWS
SOURCES = ("gold251_js", "openphish_20260926_js", "openphish_20261001", "tranco_kr_20261001", "naver_20261002")
OVERRIDES = os.path.join(REVIEWS_DIR, "label_overrides.jsonl")
OUT = os.path.join(paths.LLM_FINAL, "labels_final.jsonl")
EXCLUDED = os.path.join(paths.LLM_FINAL, "excluded.jsonl")
RISK_RANK = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
HIGH_TYPES = {"impersonation", "credentials", "apk"}
# Asks the guide grades without judgment: a resident registration or card number is
# credentials (rule 4). A password field needs judgment (a sign-in screen or a comment box),
# so the teacher decides it, and it does not by itself make a page personal_info here.
SENSITIVE_FIELDS = {"rrn", "card"}


def user_content_host(host: str) -> bool:
    return any(host == d or host.endswith("." + d) for d in USER_CONTENT_HOSTS)


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
    landing = ascii_host(obs.final_url or obs.url)
    # Brand-owned hosts include platforms such as blog.naver.com, whose own login form is genuine;
    # only the brand's own site, not what users publish on its platforms, counts as official content.
    brand_owned = obs.loaded and brands.official_brand_of(landing) is not None
    official = brand_owned and not user_content_host(landing)

    risk, type_, source, note = teacher["risk"], teacher["type"], "teacher", ""
    if risk == "HIGH" and type_ not in HIGH_TYPES:
        risk, source = "MEDIUM", "policy:high_type"
        note = f"HIGH is reserved for {sorted(HIGH_TYPES)}; {type_} is at most MEDIUM"
    if risk == "HIGH" and no_content and not (host and host.risk == "HIGH"):
        risk, type_, source = "MEDIUM", "unverifiable", "policy:no_evidence"
        note = "page returned no content; missing evidence cannot support HIGH"
    if risk == "LOW" and not brand_owned and asks & {"password", "phone"}:
        risk, type_, source = "MEDIUM", "personal_info", "policy:form"
        note = f"{', '.join(sorted(asks & {'password', 'phone'}))} field on a host the brand table does not own"
    if (risk, type_) == ("MEDIUM", "personal_info") and "email" in asks and asks <= {"email", "search"}:
        risk, type_, source = "LOW", "none", "policy:email_only"
        note = "the only personal field is an email address"
    if (risk, type_) == ("MEDIUM", "personal_info") and personal_info_unsupported(user_message(obs, brands)):
        risk, type_, source = "LOW", "none", "policy:personal_info_unsupported"
        note = "no personal field or contact wording on the first screen"
    if risk != "LOW" and official:
        risk, type_, source = "LOW", "none", "policy:official"
        note = "the brand's own site, by the brand table"
    if host and RISK_RANK[host.risk] > RISK_RANK[risk]:
        risk, type_, source, note = host.risk, host.type, "policy:host", host.reason
    return risk, type_, source, note


def apply_guide_checks(case: dict, teacher: dict, brands: Brands) -> tuple[str, str, str, str]:
    """(risk, type, label_source, note) after the guide's checks that need no judgment."""
    fetch = tool_results(case)["fetch_page"][0]
    host = grade_case(case["url"], None if "error" in fetch else fetch.get("final_url"))
    obs = observation_from_case(case)
    asks = {a for form in obs.forms for a in form.asks}
    landing = ascii_host(obs.final_url or obs.url)
    official = brands.official_brand_of(landing) is not None and not user_content_host(landing)

    risk, type_, source, note = teacher["risk"], teacher["type"], "teacher", ""
    if risk == "HIGH" and type_ not in HIGH_TYPES:
        risk, source = "MEDIUM", "policy:high_type"
        note = f"HIGH is reserved for {sorted(HIGH_TYPES)}; {type_} is at most MEDIUM"
    prompt = user_message(obs, brands)
    # Rule 5's ground: a name or phone field, or a worded form. A password, card, or similar
    # field also counts for the app's check, but rules 4 and the teacher decide those pages.
    personal = bool(asks & {"name", "phone"}) or (
        not asks & {"password", "rrn", "card", "account"} and not personal_info_unsupported(prompt))
    if risk == "LOW" and not official and personal:
        risk, type_, source = "MEDIUM", "personal_info", "policy:personal_info"
        note = "a name or phone field, or a form worded to collect personal information (guide rule 5)"
    if (risk, type_) == ("MEDIUM", "personal_info") and personal_info_unsupported(prompt):
        risk, type_, source = "LOW", "none", "policy:personal_info"
        note = "no personal field and no form worded to collect personal information (guide rule 5)"
    if risk != "HIGH" and not official and asks & SENSITIVE_FIELDS:
        risk, type_, source = "HIGH", "credentials", "policy:fields"
        note = f"{', '.join(sorted(asks & SENSITIVE_FIELDS))} field off the brand's own site (guide rule 4)"
    if risk == "LOW" and not official and (not obs.loaded or (obs.status or 0) >= 400):
        risk, type_, source = "MEDIUM", "unverifiable", "policy:unverifiable"
        note = "the page did not open or returned an error, off the brand's own site (guide rule 1)"
    if obs.apk_links and not official and (risk, type_) != ("HIGH", "apk"):
        risk, type_, source = "HIGH", "apk", "policy:apk"
        note = "an .apk link off the brand's own site (guide rule 2)"
    if official and obs.apk_links and (risk, type_) != ("MEDIUM", "apk"):
        risk, type_, source = "MEDIUM", "apk", "policy:official"
        note = "an .apk link on the brand's own site (guide rule 0)"
    elif official and not obs.apk_links and risk != "LOW":
        risk, type_, source = "LOW", "none", "policy:official"
        note = "the brand's own site, by the brand table (guide rule 0)"
    if host and RISK_RANK[host.risk] > RISK_RANK[risk]:
        risk, type_, source, note = host.risk, host.type, "policy:host", host.reason
    return risk, type_, source, note


def read_reviews(pattern: str) -> dict[str, dict]:
    """Rows with a final risk and type from review CSVs matching pattern; blank rows are not reviewed yet."""
    reviewed: dict[str, dict] = {}
    for path in sorted(glob.glob(os.path.join(REVIEWS_DIR, pattern))):
        with open(path, encoding="utf-8-sig") as f:
            for row in csv.DictReader(f):
                if row.get("final_risk") in RISK_RANK and row.get("final_type"):
                    reviewed[row["case_id"]] = row
    return reviewed


def main() -> int:
    parser = argparse.ArgumentParser(description="Merge teacher labels, policy, and review into final labels.")
    parser.add_argument("--teacher", choices=("questions", "guide", "original"), default="questions",
                        help="questions: the teacher's answers and the verdict table; guide: labels_*_guide.jsonl "
                             "and the guide's checks (run 6); original: the criteria and policy (runs 3 to 5)")
    args = parser.parse_args()
    reviewed = read_reviews("review_*.csv")
    assistant = read_reviews("assistant_review_*.csv")
    overrides = {row["case_id"]: row for row in read_jsonl(OVERRIDES)} if os.path.exists(OVERRIDES) else {}

    brands = Brands.load()
    table = VerdictTable.load()
    out_rows, excluded = [], []
    for source in SOURCES:
        cases = {c["case_id"]: c for c in read_jsonl(os.path.join(SOURCES_DIR, f"cases_{source}.jsonl"))}
        if args.teacher in ("questions", "guide"):
            labels = {r["case_id"]: r for r in read_jsonl(os.path.join(SOURCES_DIR, f"labels_{source}_{args.teacher}.jsonl"))}
        else:
            labels = {r["case_id"]: r for r in read_jsonl(os.path.join(SOURCES_DIR, f"labels_{source}.jsonl"))}
            addendum_path = os.path.join(SOURCES_DIR, f"labels_{source}_addendum.jsonl")
            if os.path.exists(addendum_path):
                labels.update({r["case_id"]: r for r in read_jsonl(addendum_path) if "verdict" in r})
        if cases.keys() != labels.keys():
            sys.exit(f"{source}: cases and labels cover different case IDs")
        for case_id, label in labels.items():
            if "verdict" not in label:
                sys.exit(f"{case_id}: no teacher verdict ({label.get('error')})")
            teacher = label["verdict"]
            obs = observation_from_case(cases[case_id])
            blocked = bot_check(obs)
            if blocked:
                excluded.append({"case_id": case_id, "source": source, "url": cases[case_id]["url"],
                                 "reason": f"bot_check:{blocked}"})
                continue
            extra = {}
            if args.teacher == "questions":
                known = verdict_facts.page_facts(obs, verdict_facts.url_facts(cases[case_id]["url"], brands), brands)
                decision = table.decide(verdict_facts.answer_facts(teacher, obs, known, brands))
                extra = {"answers": teacher, "facts": known.to_json()}
                teacher = {"risk": decision.risk, "type": decision.type, "reason": decision.reason}
            row = {"case_id": case_id, "source": source, "url": cases[case_id]["url"],
                   "teacher_risk": teacher["risk"], "teacher_type": teacher["type"], **extra}
            if case_id in reviewed:
                r = reviewed[case_id]
                row.update(risk=r["final_risk"], type=r["final_type"], label_source="human_review", note=r["note"])
            elif case_id in assistant:
                r = assistant[case_id]
                row.update(risk=r["final_risk"], type=r["final_type"], label_source="assistant_review", note=r["note"])
            elif case_id in overrides:
                o = overrides[case_id]
                row.update(risk=o["risk"], type=o["type"], label_source="override", note=o["reason"])
            elif args.teacher == "questions":
                row.update(risk=teacher["risk"], type=teacher["type"], label_source="verdict_table", note=teacher["reason"])
            else:
                checks = apply_guide_checks if args.teacher == "guide" else apply_policy
                risk, type_, label_source, note = checks(cases[case_id], teacher, brands)
                row.update(risk=risk, type=type_, label_source=label_source, note=note)
            out_rows.append(row)

    known = {r["case_id"] for r in out_rows + excluded}
    unknown = (reviewed.keys() | assistant.keys() | overrides.keys()) - known
    if unknown:
        sys.exit(f"review or override rows without a case: {sorted(unknown)}")

    with open(OUT, "w", encoding="utf-8") as f:
        f.writelines(json.dumps(r, ensure_ascii=False) + "\n" for r in out_rows)

    with open(EXCLUDED, "w", encoding="utf-8") as f:
        f.writelines(json.dumps(r, ensure_ascii=False) + "\n" for r in excluded)

    print(f"wrote {len(out_rows)} rows to {OUT}; {len(excluded)} bot-check pages to {EXCLUDED}")
    for source in SOURCES:
        rows = [r for r in out_rows if r["source"] == source]
        print(f"  {source}: risk {dict(collections.Counter(r['risk'] for r in rows))}, "
              f"label_source {dict(collections.Counter(r['label_source'] for r in rows))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
