"""Assign each labeled case to train, validation, or test.

Design:
    Grouped by site.  All cases on one registrable domain land in the same split;
        otherwise the model could memorize a site seen in training and inflate
        test scores. Sites are resolved with the bundled Public Suffix List
        (shared/public_suffix.py), so `a.vercel.app` and `b.vercel.app` are different sites.
    Stratified by risk.  Each site group is stratified by its highest risk, so
        the rare HIGH class reaches validation and test in proportion.
    Balanced by deficit.  Within a stratum, larger groups are placed first, each
        into the split furthest below its target size.
    Human-reviewed cases go to test first.  Their labels are the most reliable,
        which makes them the best measure of accuracy.

Validation is for checkpoint selection only; test is used once, at the end.
The assignment is deterministic for a given seed and label file.

Usage:
    python -m guadian_ml.llm.dataset.split [--seed 20260926]
"""

from __future__ import annotations

import argparse
import collections
import json
import random
import sys
from urllib.parse import urlsplit

from guadian_ml import paths
from guadian_ml.shared import public_suffix

LABELS = paths.LLM_DATA / "labels_final.jsonl"
OUT = paths.LLM_DATA / "splits.jsonl"
TARGETS = {"train": 0.7, "validation": 0.1, "test": 0.2}
RISK_ORDER = ("HIGH", "MEDIUM", "LOW")


def group_risk(rows: list[dict]) -> str:
    """The highest risk in a site group; rare classes drive stratification."""
    return next(r for r in RISK_ORDER if any(row["risk"] == r for row in rows))


def main() -> int:
    parser = argparse.ArgumentParser(description="Grouped, stratified train/validation/test split.")
    parser.add_argument("--seed", type=int, default=20260926)
    args = parser.parse_args()

    suffixes = public_suffix.default()
    with open(LABELS, encoding="utf-8") as f:
        rows = [json.loads(line) for line in f if line.strip()]

    groups: dict[str, list[dict]] = collections.defaultdict(list)
    for row in rows:
        host = urlsplit(row["url"]).hostname or row["url"]
        groups[suffixes.registrable_domain(host) or host].append(row)

    rng = random.Random(args.seed)
    strata: dict[str, list[str]] = collections.defaultdict(list)
    for site in sorted(groups):
        strata[group_risk(groups[site])].append(site)

    assignment: dict[str, str] = {}
    for sites in strata.values():
        rng.shuffle(sites)   # breaks ties among equal-sized groups
        total = sum(len(groups[s]) for s in sites)
        filled = collections.Counter()
        reviewed = [s for s in sites if any(r["label_source"] == "human_review" for r in groups[s])]
        for site in reviewed:
            assignment[site] = "test"
            filled["test"] += len(groups[site])
        rest = sorted((s for s in sites if s not in assignment), key=lambda s: -len(groups[s]))
        for site in rest:
            split = max(TARGETS, key=lambda name: TARGETS[name] * total - filled[name])
            assignment[site] = split
            filled[split] += len(groups[site])

    with open(OUT, "w", encoding="utf-8") as f:
        for site, members in sorted(groups.items()):
            for row in members:
                f.write(json.dumps({"case_id": row["case_id"], "site": site, "split": assignment[site]}) + "\n")

    table = collections.Counter((assignment[s], r["risk"]) for s, members in groups.items() for r in members)
    reviewed = collections.Counter(assignment[s] for s, members in groups.items()
                                   for r in members if r["label_source"] == "human_review")
    print(f"wrote {len(rows)} rows ({len(groups)} sites) to {OUT}")
    for split in TARGETS:
        counts = {risk: table[(split, risk)] for risk in reversed(RISK_ORDER)}
        print(f"  {split:<10} {sum(counts.values()):>4}  {counts}  human_review={reviewed[split]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
