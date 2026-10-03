"""Build URL lists for llm.dataset.collect from three sources.

    naver      Naver Search API (web documents) for the page types the model
               misses: consultation and lead forms, events, investment, payment.
               Organic results, not ads, so nothing is clicked and no advertiser
               is billed; the label depends on the page, not on how it was reached.
               Needs NAVER_CLIENT_ID and NAVER_CLIENT_SECRET from an app in NAVER API
               HUB (Naver Cloud Platform), where the search API moved in 2026;
               developers.naver.com stopped taking new apps on 2026-07-31.
    tranco     Ordinary Korean sites from a Tranco top-sites CSV, for LOW cases.
    openphish  The OpenPhish community feed, for impersonation and credential pages.
               Its terms allow research use and forbid passing the data on, so the
               lists stay in the git-ignored ml/data/llm/.

Ad networks and ad libraries are not used: clicking ads by script is click
fraud, and the Meta and Google ad libraries give no landing URLs for Korean
commercial ads (checked 2026-10-01).

Every list drops URLs whose site is already in an earlier list (any
ml/data/llm/urls_*.jsonl), so new cases never share a site with existing ones
and the grouped split stays clean, and keeps at most --per-site URLs per site.

Usage:
    python -m guadian_ml.llm.dataset.urls naver -o ml/data/llm/urls_naver_20261001.jsonl
    python -m guadian_ml.llm.dataset.urls tranco top-1m.csv --limit 300 -o ml/data/llm/urls_tranco_kr.jsonl
    python -m guadian_ml.llm.dataset.urls openphish --limit 400 -o ml/data/llm/urls_openphish_20261001.jsonl
"""

from __future__ import annotations

import argparse
import csv
import glob
import html
import json
import os
import re
import sys
import time
from collections import Counter
from datetime import date
from urllib.parse import urlsplit

import requests

from guadian_ml import paths
from guadian_ml.shared import public_suffix

NAVER_WEB = "https://naverapihub.apigw.ntruss.com/search/v1/webkr"
OPENPHISH_FEED = "https://openphish.com/feed.txt"
# Queries by the page type they are meant to surface. Phrased as a person
# looking for the service would search, so results are the landing pages
# advertisers also use.
NAVER_QUERIES = {
    "personal_info": [
        "보험료 무료 상담 신청", "암보험 비교 상담", "실비보험 무료 견적", "상조 가입 상담", "보청기 무료체험 신청",
        "임플란트 무료 상담 예약", "치아교정 상담 신청", "다이어트 한약 무료 상담", "탈모 치료 상담 예약",
        "법률 무료 상담 신청", "개인회생 무료 상담", "학원 무료 상담 신청", "자격증 무료 상담", "인테리어 무료 견적",
        "이사 견적 신청", "렌터카 장기렌트 견적", "정수기 렌탈 상담", "태양광 설치 상담", "분양 상담 예약",
        "결혼정보회사 무료 상담",
    ],
    "investment": [
        "주식 리딩방", "코인 투자 수익", "부업 재택 고수익", "재테크 무료 강의", "해외선물 수익 인증",
        "부동산 경매 강의 신청", "비트코인 무료 리딩",
    ],
    "payment": [
        "다이어트 보조제 할인", "건강기능식품 특가", "중고차 할부", "무료 체험 정기결제", "해외 직구 명품 저렴",
    ],
    "urgency": [
        "경품 이벤트 응모", "무료 증정 이벤트", "선착순 무료 체험", "당첨 확인 이벤트", "출석 체크 이벤트 경품",
    ],
    "loan": [
        "대출 한도 조회", "저금리 대환대출", "소상공인 대출 상담", "무직자 대출", "신용대출 비교",
    ],
    "ordinary": [
        "온라인 쇼핑몰 여름 세일", "호텔 예약", "항공권 특가", "맛집 예약", "영화 예매", "전자제품 할인",
        "도서 구매", "꽃 배달", "택배 조회", "지역 축제 일정",
    ],
}


_SUFFIXES = public_suffix.default()


def site_of(url: str) -> str:
    """The split's grouping key: the registrable domain (llm.dataset.split)."""
    host = (urlsplit(url).hostname or "").lower()
    return _SUFFIXES.registrable_domain(host) or host


def known_sites(exclude: str) -> set[str]:
    """Sites in every existing URL list except the one being written."""
    sites = set()
    for path in glob.glob(str(paths.LLM_DATA / "urls_*.jsonl")):
        if os.path.abspath(path) == os.path.abspath(exclude):
            continue
        with open(path, encoding="utf-8") as f:
            sites |= {site_of(json.loads(line)["url"]) for line in f if line.strip()}
    return sites


def keep(rows: list[dict], seen: set[str], per_site: int, limit: int | None) -> list[dict]:
    """Drop known sites and duplicate URLs; cap URLs per site; stop at limit."""
    out, per, urls = [], Counter(), set()
    for row in rows:
        site = site_of(row["url"])
        if not site or site in seen or row["url"] in urls or per[site] >= per_site:
            continue
        per[site] += 1
        urls.add(row["url"])
        out.append(row)
        if limit and len(out) >= limit:
            break
    return out


def naver_rows(display: int, pages: int) -> list[dict]:
    client_id, secret = os.environ.get("NAVER_CLIENT_ID"), os.environ.get("NAVER_CLIENT_SECRET")
    if not client_id or not secret:
        sys.exit("set NAVER_CLIENT_ID and NAVER_CLIENT_SECRET (NAVER API HUB app, web document search)")
    headers = {"X-NCP-APIGW-API-KEY-ID": client_id, "X-NCP-APIGW-API-KEY": secret}
    rows = []
    for category, queries in NAVER_QUERIES.items():
        for query in queries:
            for page in range(pages):
                response = requests.get(NAVER_WEB, headers=headers, timeout=15,
                                        params={"query": query, "display": display, "start": 1 + page * display,
                                                "format": "json"})
                response.raise_for_status()
                for item in response.json().get("items", []):
                    rows.append({"url": item["link"], "source": "naver", "category": category, "query": query,
                                 "title": re.sub(r"<[^>]+>", "", html.unescape(item.get("title", "")))})
                time.sleep(0.1)   # well under the HUB's 50 requests per second
    return rows


def tranco_rows(csv_path: str) -> list[dict]:
    """Korean sites (.kr) in rank order."""
    rows = []
    with open(csv_path, encoding="utf-8") as f:
        for rank, domain in csv.reader(f):
            if domain.endswith(".kr"):
                rows.append({"url": f"https://{domain}/", "source": "tranco", "rank": int(rank)})
    return rows


def openphish_rows() -> list[dict]:
    response = requests.get(OPENPHISH_FEED, timeout=30)
    response.raise_for_status()
    return [{"url": line.strip(), "source": "openphish"} for line in response.text.splitlines()
            if line.strip().startswith("http")]


def main() -> int:
    parser = argparse.ArgumentParser(description="Build a URL list for llm.dataset.collect.")
    parser.add_argument("source", choices=("naver", "tranco", "openphish"))
    parser.add_argument("csv", nargs="?", help="Tranco top-sites CSV (tranco only)")
    parser.add_argument("-o", "--out", required=True)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--per-site", type=int, default=2, help="at most this many URLs per site")
    parser.add_argument("--display", type=int, default=30, help="naver: results per call (max 100)")
    parser.add_argument("--pages", type=int, default=1, help="naver: calls per query")
    args = parser.parse_args()

    if args.source == "naver":
        rows = naver_rows(args.display, args.pages)
    elif args.source == "tranco":
        if not args.csv:
            sys.exit("tranco needs the top-sites CSV path")
        rows = tranco_rows(args.csv)
    else:
        rows = openphish_rows()

    rows = keep(rows, known_sites(args.out), args.per_site, args.limit)
    stamp = date.today().strftime("%Y%m%d")
    prefix = {"naver": "nv", "tranco": "tr", "openphish": "op"}[args.source]
    with open(args.out, "w", encoding="utf-8") as f:
        for i, row in enumerate(rows, 1):
            f.write(json.dumps({"case_id": f"{prefix}{stamp}_{i:04d}", **row,
                                "listed_on": stamp}, ensure_ascii=False) + "\n")
    print(f"wrote {len(rows)} URLs to {args.out}")
    print("  by category:", dict(Counter(r.get("category", r["source"]) for r in rows)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
