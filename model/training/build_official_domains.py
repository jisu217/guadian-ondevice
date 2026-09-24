"""Rebuild model/data/official_domains.txt from a Tranco top-sites export.

    python3 build_official_domains.py --tranco /path/to/top-1m.csv

Download the input from https://tranco-list.eu (top-1m.csv.zip, unzip
first) — it isn't committed here because it's a third-party 1M-row file
you can always re-fetch, and only the top `--rank-limit` rows are used
anyway. The output replaces model/data/official_domains.txt, which both
url_features.py (training) and UrlFeatureExtractor.kt (runtime, via the
`resources.srcDir("model/data")` entry in build.gradle.kts) read directly.

BRAND_DOMAINS and SEED_DOMAINS below can't be imported from the Kotlin
rule engine (android/app/.../rules/OfficialDomains.kt) or the pre-Tranco
feature extractor, so they're kept here by hand — same pattern as the
model centroids in OnDeviceModelJudge.kt.
"""
from __future__ import annotations

import argparse
import csv
from pathlib import Path

DEFAULT_OUTPUT = Path(__file__).parents[1] / "data" / "official_domains.txt"
DEFAULT_RANK_LIMIT = 50_000

# Mirrors android/app/src/main/kotlin/com/guardian/ondevice/rules/OfficialDomains.kt
BRAND_DOMAINS = {
    "coupang.com", "coupangeats.com",
    "naver.com", "navercorp.com", "pay.naver.com",
    "kakao.com", "kakaopay.com", "kakaocorp.com",
    "kbstar.com", "kbcard.com", "kbfg.com",
    "shinhan.com", "shinhancard.com",
}

# The original 27-domain allowlist (pre-Tranco), including the synthetic
# ".test" domains used by seed fixture rows in sample_urls.csv — Tranco has
# no opinion on those, so they must stay listed explicitly.
SEED_DOMAINS = {
    "example.com", "support.example.com", "docs.example.com", "news.example.com",
    "www.google.com", "www.apple.com", "www.samsung.com", "www.microsoft.com",
    "www.cloudflare.com", "www.wikipedia.org", "github.com", "docs.github.com",
    "www.kakaocorp.com", "www.naver.com", "www.toss.im", "www.iana.org",
    "developer.android.com", "kotlinlang.org", "www.python.org",
    "safe-reference.test", "trusted-service.test", "verified-support.test",
    "official-store.test", "customer-center.test", "docs.safe-example.test",
    "news.safe-example.test", "status.safe-example.test",
}


def load_tranco_domains(path: Path, rank_limit: int) -> set[str]:
    domains = set()
    with path.open(newline="") as handle:
        for row in csv.reader(handle):
            if len(row) != 2:
                continue
            rank_str, domain = row
            if not rank_str.isdigit():
                continue
            if int(rank_str) > rank_limit:
                break
            domains.add(domain.strip().lower())
    return domains


def build_domain_set(tranco_path: Path, rank_limit: int) -> set[str]:
    return load_tranco_domains(tranco_path, rank_limit) | BRAND_DOMAINS | SEED_DOMAINS


def save_domains(domains: set[str], path: Path = DEFAULT_OUTPUT) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(sorted(domains)) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tranco", required=True, type=Path, help="Path to Tranco's top-1m.csv")
    parser.add_argument("--rank-limit", type=int, default=DEFAULT_RANK_LIMIT)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    domains = build_domain_set(args.tranco, args.rank_limit)
    save_domains(domains, args.out)
    print(f"wrote {len(domains)} domains to {args.out}")


if __name__ == "__main__":
    main()
