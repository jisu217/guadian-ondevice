"""Check hand-written doc tables against the generated metrics.

    python3 scripts/check_doc_metrics.py

model/evaluation/metrics.json (written by train_model.py) and
model/evaluation/local-resolution-report.md (written by
`./gradlew :core:judge:writeLocalResolutionReport`) are the only sources of
current numbers. Every table row in DOCS whose first cell names a known
metric is compared against them, cell by cell, as the reports format it
("75.00%", or an integer count). Rows for other versions (history tables)
and prose sentences are not checked.

Exits 1 and lists each mismatch, or each doc with no checkable row (a sign
that a doc was restructured and its numbers silently left the check).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METRICS = ROOT / "model" / "evaluation" / "metrics.json"
LOCAL_RESOLUTION_REPORT = ROOT / "model" / "evaluation" / "local-resolution-report.md"
DOCS = [
    "README.md",
    "model/README.md",
    "model/evaluation/model-card.md",
    "model/docs/model-evolution.md",
    "docs/technical-report.md",
]
LABELS = ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]


def percent(value: float) -> str:
    return f"{value:.2%}"


def expected_rows(metrics: dict, local_resolution: dict[str, list[str]]) -> dict[str, list[str]]:
    """First-cell text -> expected leading cells of that row."""
    holdout = metrics["holdout"]
    by_source = metrics["holdout_by_source"]
    rows: dict[str, list[str]] = {
        "Holdout accuracy": [percent(holdout["accuracy"])],
        "Holdout macro F1": [percent(holdout["macro_f1"])],
        "5-fold CV accuracy": [percent(metrics["cv"]["accuracy"])],
        "5-fold CV macro F1": [percent(metrics["cv"]["macro_f1"])],
        # Current-version row of the version comparison table.
        metrics["version"]: [
            percent(holdout["accuracy"]),
            percent(holdout["macro_f1"]),
            percent(holdout["per_label"]["HIGH"]["f1"]),
            percent(by_source["synthetic"]["accuracy"]),
            percent(by_source["non_synthetic"]["accuracy"]),
            percent(metrics["cv"]["accuracy"]),
            percent(metrics["cv"]["macro_f1"]),
        ],
    }
    baseline = [percent(metrics["baseline"]["holdout_accuracy"])]
    for name in ("Rule baseline accuracy (same split)", "Baseline accuracy on same split", "Baseline accuracy (same split)"):
        rows[name] = baseline
    for label in LABELS:
        current = holdout["per_label"][label]
        rows[label] = [percent(current["precision"]), percent(current["recall"]), percent(current["f1"])]
    for bucket, count in holdout["error_buckets"].items():
        rows[bucket] = [str(count)]
    rows.update(local_resolution)
    return rows


def local_resolution_rows(report: str) -> dict[str, list[str]]:
    """Resolved/correct counts from the report's holdout section."""
    section = report.split("## 2. Holdout Split", 1)[1].split("\n## ", 1)[0]
    rows: dict[str, list[str]] = {}
    resolved_total = correct_total = 0
    for cells in table_rows(section):
        if len(cells) >= 3 and cells[1].isdigit() and cells[2].isdigit():
            rows[cells[0]] = [cells[1], cells[2]]
            resolved_total += int(cells[1])
            correct_total += int(cells[2])
    rows["Total local"] = [str(resolved_total), str(correct_total)]
    return rows


def table_rows(text: str):
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped.startswith("|") or set(stripped) <= set("|:- "):
            continue
        yield [normalize(cell) for cell in stripped.strip("|").split("|")]


def normalize(cell: str) -> str:
    return cell.strip().replace("`", "").replace("**", "")


def check_doc(text: str, expected: dict[str, list[str]]) -> tuple[int, list[str]]:
    checked = 0
    problems = []
    for cells in table_rows(text):
        want = expected.get(cells[0])
        if want is None:
            continue
        got = cells[1 : 1 + len(want)]
        checked += 1
        if got != want:
            problems.append(f"row '{cells[0]}': expected {want}, found {got}")
    return checked, problems


def main() -> int:
    metrics = json.loads(METRICS.read_text())
    expected = expected_rows(metrics, local_resolution_rows(LOCAL_RESOLUTION_REPORT.read_text()))

    failed = False
    for doc in DOCS:
        checked, problems = check_doc((ROOT / doc).read_text(), expected)
        if checked == 0:
            problems.append("no metric table rows found")
        for problem in problems:
            print(f"[doc-metrics] FAIL {doc}: {problem}", file=sys.stderr)
        failed = failed or bool(problems)
        if not problems:
            print(f"[doc-metrics] PASS {doc}: {checked} rows match {metrics['version']} metrics")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
