"""Collect judgment inputs for a list of landing-page URLs.

For each URL this script gathers the evidence the original GUADIAN server
collected before asking its LLM judge: the fetched page text, HTML signals,
blocklist membership, official-domain lookups, and domain age. Each case is
written as one JSON line in the `cases.jsonl` format used by
GuADian23-model-compare, so `vendor/build_prompt.py` can assemble the exact
prompt the service would have sent.

Sources:
    Tool output format   GuADian23-judge-research server/judge_agent.py, run_agent.run_tool
    Collection order     GuADian23-judge-research server/judge_agent.py, _prefetch (oneshot=True)
    Blocklist handling   GuADian23-judge-research server/main.py, BLOCKLISTS, _parse_list, blocked_by

Deviations from the service, and why:
    Sequential collection.  The service collected in parallel under a 7.5 s
        budget and dropped late results. Offline collection has no latency
        budget, so every tool result is kept.
    Domain age is awaited.  The service did not wait for RDAP during prefetch.
        Waiting yields a known age more often, which makes the label better
        informed. Label and input still come from the same observation.
    lookup_cache is always empty.  A previous verdict would leak an earlier
        model's label into the input. build_prompt.DEFAULT_EXCLUDE drops this
        tool as well; the record is kept only so the case format stays complete.
    Pages are rendered with JavaScript (default).  The service fetched raw HTML,
        so pages drawn by scripts arrived empty and could only be judged
        "unverifiable". The app's Page Content Inspection renders pages in a
        WebView with JavaScript on; rendering here in headless Chromium with a
        mobile profile gives training inputs that match what the app will see.
        `--renderer requests` reproduces the service's fetch for comparison.
    Encoding is detected when the server omits it (requests renderer only).
        For a text/html response without a charset, requests decodes as
        ISO-8859-1, which turns UTF-8 Korean into mojibake. The service had this
        defect; the collector corrects it (see _get_with_detected_encoding).

Safety:
    The browser runs untrusted pages, including known phishing. Each page gets
    a fresh context with downloads refused and no stored state.

Usage:
    python -m guadian_ml.llm.dataset.collect URLS.jsonl -o ml/data/llm/cases_NAME.jsonl

URLS.jsonl holds one object per line with a required `url`. An optional
`case_id` is kept; every other field is stored under `meta`. Reruns skip
cases already present in the output file.
"""

from __future__ import annotations

import argparse
import contextlib
import json
import os
import sys
import time
from datetime import datetime, timezone
from typing import Callable

import requests

from guadian_ml import paths
from guadian_ml.llm.vendor import judge_agent as ja

DATA_DIR = paths.LLM_DATA
BLOCKLIST_CACHE = str(DATA_DIR / "blocklist.txt")
DOMAIN_AGE_DB = str(DATA_DIR / "domain_age.sqlite")
BLOCKLIST_MAX_AGE_S = 24 * 3600

# Same sources as server/main.py BLOCKLISTS. License: CC BY-NC-SA 3.0 (non-commercial).
BLOCKLISTS = [
    ("ut1-phishing", "https://raw.githubusercontent.com/smed79/ut1/master/phishing/domains"),
    ("ut1-malware", "https://raw.githubusercontent.com/smed79/ut1/master/malware/domains"),
]


_vendored_get_capped = ja._get_capped


def _get_with_detected_encoding(url: str, deadline: float) -> requests.Response:
    """Wrap judge_agent._get_capped so pages without a declared charset are decoded correctly."""
    response = _vendored_get_capped(url, deadline)
    if "charset" not in response.headers.get("content-type", "").lower():
        declared = requests.utils.get_encodings_from_content(response.content.decode("ascii", "ignore"))
        response.encoding = declared[0] if declared else response.apparent_encoding
    return response


# fetch_chain resolves _get_capped from the module namespace at call time.
ja._get_capped = _get_with_detected_encoding

# Same device profile as the service's User-Agent (a Galaxy S24-class Android phone).
MOBILE_VIEWPORT = {"width": 412, "height": 915}
NAVIGATION_TIMEOUT_MS = 15_000
SETTLE_TIMEOUT_MS = 5_000


class BrowserFetcher:
    """Render a page in headless Chromium and return the fields fetch_chain returns."""

    def __enter__(self) -> "BrowserFetcher":
        from playwright.sync_api import sync_playwright
        self._playwright = sync_playwright().start()
        self._browser = self._playwright.chromium.launch()
        return self

    def __exit__(self, *exc) -> None:
        self._browser.close()
        self._playwright.stop()

    def __call__(self, url: str) -> dict:
        context = self._browser.new_context(
            user_agent=ja._MOBILE_UA, viewport=MOBILE_VIEWPORT, is_mobile=True, has_touch=True,
            locale="ko-KR", accept_downloads=False)
        try:
            page = context.new_page()
            navigations: list[tuple[str, int]] = []   # main-frame document responses, in order

            def on_response(response) -> None:
                if response.request.is_navigation_request() and response.frame == page.main_frame:
                    navigations.append((response.url, response.status))

            page.on("response", on_response)
            # "commit" returns once the first response arrives; network and DNS failures still raise.
            page.goto(url, wait_until="commit", timeout=NAVIGATION_TIMEOUT_MS)
            # Slow pages and long-polling pages never finish loading. Waiting is bounded,
            # and whatever has rendered by then is the observation.
            for state, timeout_ms in (("domcontentloaded", NAVIGATION_TIMEOUT_MS), ("networkidle", SETTLE_TIMEOUT_MS)):
                try:
                    page.wait_for_load_state(state, timeout=timeout_ms)
                except Exception:
                    break
            html = page.content()
            full_text = ja._text_of(html)
            return {
                "final_url": page.url,
                "status": navigations[-1][1] if navigations else 0,
                "chain": [u for u, _ in navigations] or [page.url],
                "html": html,
                "text": full_text[:ja.MAX_PAGE_CHARS],
                "text_chars": len(full_text),
                "full_text": full_text,
            }
        finally:
            context.close()


def parse_blocklist(text: str) -> set[str]:
    """Extract domains from a hosts-style or one-domain-per-line list (server/main.py _parse_list)."""
    domains = set()
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        domain = (parts[-1] if len(parts) > 1 else parts[0]).lower().strip(".")
        if domain in ("localhost", "localhost.localdomain", "broadcasthost", "0.0.0.0", "::1"):
            continue
        if "." in domain and not domain.replace(".", "").isdigit():
            domains.add(domain)
    return domains


def load_blocklist(refresh: bool = False) -> dict[str, str]:
    """Return a domain -> list-name table, downloading the lists when the cache is missing or stale."""
    stale = (not os.path.exists(BLOCKLIST_CACHE)
             or time.time() - os.path.getmtime(BLOCKLIST_CACHE) >= BLOCKLIST_MAX_AGE_S)
    if refresh or stale:
        lines = []
        for name, url in BLOCKLISTS:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
            lines += [f"{name}\t{domain}" for domain in sorted(parse_blocklist(response.text))]
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(BLOCKLIST_CACHE, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
    table: dict[str, str] = {}
    with open(BLOCKLIST_CACHE, encoding="utf-8") as f:
        for line in f:
            name, _, domain = line.rstrip("\n").partition("\t")
            table.setdefault(domain, name)
    return table


def blocked_by(table: dict[str, str], host: str) -> str | None:
    """Return the list containing the host or any parent domain (server/main.py blocked_by)."""
    labels = host.split(".")
    for i in range(len(labels) - 1):
        name = table.get(".".join(labels[i:]))
        if name:
            return name
    return None


def collect(url: str, blocklist: dict[str, str], fetcher: Callable[[str], dict]) -> list[dict]:
    """Run the oneshot prefetch for one URL and return its tool outputs.

    `fetcher` is BrowserFetcher or judge_agent.fetch_chain; both return the same fields.

    Each entry is {"tool", "args", "output"} with `output` serialized exactly as
    run_agent.record() stored it, because build_prompt parses these strings.
    """
    outputs: list[dict] = []

    def record(tool: str, args: dict, result: dict) -> dict:
        outputs.append({"tool": tool, "args": args, "output": json.dumps(result, ensure_ascii=False)})
        return result

    def official_domain_of(query: str) -> dict:
        # A domain-shaped query asks "whose official domain is this?"; a name asks
        # "what are this brand's official domains?". The service branches the same way.
        args = {"brand": query}
        if ja._looks_like_domain(query):
            hit = ja.brand_of_domain(query)
            if hit:
                brand, domains = hit
                return record("official_domain_of", args, {
                    "domain": query, "brand": brand, "official_domains": domains, "known": True,
                    "note": f"{query}은(는) {brand}의 공식 도메인이다"})
            return record("official_domain_of", args, {
                "domain": query, "known": False,
                "note": f"{query}은(는) 공식 도메인 표에 없다 — 표에 없다는 것은 사칭의 근거가 아니다"})
        domains = ja.official_domains_of(query)
        return record("official_domain_of", args, {"brand": query, "official_domains": domains, "known": bool(domains)})

    def domain_age(host: str) -> None:
        site = ja.site_of(host) or host
        days, note = ja.domain_age_days(site, DOMAIN_AGE_DB, wait_s=ja.RDAP_BG_TIMEOUT_S)
        record("domain_age", {"host": host}, {
            "domain": site, "age_days": days,
            "young": days is not None and days < ja.YOUNG_DOMAIN_DAYS, "note": note})

    host = ja.host_of(url) or ""
    site = ja.site_of(host) or host

    # Page branch: fetch, then signals from the same HTML, then the lookups that depend on them.
    try:
        page = fetcher(url)
        fetch = record("fetch_page", {"url": url},
                       {k: v for k, v in page.items() if k not in ("html", "full_text")})
    except Exception as e:
        fetch = record("fetch_page", {"url": url}, {"error": f"{type(e).__name__}: {str(e)[:120]}"})
    if "error" not in fetch:
        final_url = page["final_url"]
        signals = record("page_signals", {"url": final_url},
                         ja.page_signals(page["html"], final_url, body_text=page["full_text"]))
        if signals.get("js_only") or (fetch.get("status") or 0) >= 400:
            # Without readable text, the destination domain itself is the main evidence.
            official_domain_of(ja.host_of(final_url) or site)
        final_site = ja.site_of(final_url) or ""
        if final_site and final_site != site:
            domain_age(final_site)
        for brand in [b for b in (signals.get("brand_mentions") or []) if isinstance(b, str)][:6]:
            official_domain_of(brand)

    # Lookup branch.
    listed = blocked_by(blocklist, host)
    record("check_blocklist", {"host": host}, {"host": host, "listed": bool(listed), "list": listed})
    record("lookup_cache", {"site": site}, {"site": site, "previous": None})

    # Origin domain age.
    domain_age(site)
    return outputs


def main() -> int:
    parser = argparse.ArgumentParser(description="Collect judgment inputs for a URL list.")
    parser.add_argument("urls", help="JSONL file, one object with a `url` field per line")
    parser.add_argument("-o", "--out", required=True, help="output cases JSONL (appended to on rerun)")
    parser.add_argument("--limit", type=int, help="process only the first N URLs")
    parser.add_argument("--refresh-blocklist", action="store_true", help="download the blocklist even if cached")
    parser.add_argument("--renderer", choices=("browser", "requests"), default="browser",
                        help="browser renders JavaScript (default); requests reproduces the original service")
    args = parser.parse_args()

    blocklist = load_blocklist(args.refresh_blocklist)
    done: set[str] = set()
    if os.path.exists(args.out):
        with open(args.out, encoding="utf-8") as f:
            done = {json.loads(line)["case_id"] for line in f if line.strip()}

    with open(args.urls, encoding="utf-8") as f:
        rows = [json.loads(line) for line in f if line.strip()]
    if args.limit:
        rows = rows[: args.limit]

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with contextlib.ExitStack() as stack:
        fetcher = stack.enter_context(BrowserFetcher()) if args.renderer == "browser" else ja.fetch_chain
        out = stack.enter_context(open(args.out, "a", encoding="utf-8"))
        for i, row in enumerate(rows, 1):
            case_id = row.get("case_id") or f"case_{i:04d}"
            if case_id in done:
                continue
            started = time.monotonic()
            tool_outputs = collect(row["url"], blocklist, fetcher)
            case = {
                "case_id": case_id,
                "url": row["url"],
                "input": {"url": row["url"], "click_url": None, "tool_outputs": tool_outputs},
                "meta": {k: v for k, v in row.items() if k not in ("case_id", "url")},
                "renderer": args.renderer,
                "collected_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            }
            out.write(json.dumps(case, ensure_ascii=False) + "\n")
            out.flush()
            fetch = json.loads(tool_outputs[0]["output"])
            state = fetch.get("error") or f"{fetch.get('status')} {fetch.get('text_chars')} chars"
            print(f"[{i}/{len(rows)}] {case_id} {state} ({time.monotonic() - started:.1f}s)", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
