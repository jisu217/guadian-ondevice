"""The verdict table's facts from a URL and a page observation, as the app computes them.

Port of core/llm UrlSignals.kt and PageFacts.kt (docs/design.md, section 4), so labels
and offline scores use the same signals as the phone; fixtures/llm/facts_cases.json,
written by guadian_ml.llm.contract.fixtures, holds inputs with the facts expected
here for the Kotlin parity test.

`claim_kind` turns the model's `claims` answer into the table's claim: a brand in the
brand table whose domain the landing host is not, a brand the table does not list, or
nothing.
"""

from __future__ import annotations

import dataclasses
import functools
import re
from urllib.parse import urlsplit

from guadian_ml import paths
from guadian_ml.llm.contract import host_rules
from guadian_ml.llm.contract.answer import form_worded_for_contact
from guadian_ml.llm.contract.prompt import DEFAULT_PAGE_CHARS, MIN_ALIAS_CHARS, Brands, PageObservation, ascii_host
from guadian_ml.llm.contract.verdict_table import Facts
from guadian_ml.resources.blocklist import BloomFilter
from guadian_ml.shared import public_suffix

# ".apk" followed by a non-alphanumeric or the end, in path and query (UrlRuleSignals.kt).
APK_PATTERN = re.compile(r"\.apk(?![a-z0-9])")
SENSITIVE_ASKS = {"rrn", "card"}
PERSONAL_ASKS = {"name", "phone"}


@functools.cache
def _blocklist() -> BloomFilter:
    return BloomFilter.from_bytes(paths.BLOCKLIST.read_bytes())


def _url_host(url: str) -> str | None:
    """UrlHost.of: lowercased, "www." stripped, https assumed when the scheme is missing."""
    url = url.strip()
    if not url:
        return None
    host = urlsplit(url if "://" in url else "https://" + url).hostname
    return host.removeprefix("www.") or None if host else None


def listed_domain(url: str) -> str | None:
    """The blocklisted domain the URL's host falls under, walking up to the registrable domain."""
    host = _url_host(url)
    registrable = public_suffix.default().registrable_domain(host) if host else None
    if not registrable:
        return None
    while host and len(host) >= len(registrable):
        if host in _blocklist():
            return host
        host = host.partition(".")[2]
    return None


def has_apk(url: str) -> bool:
    """UrlRuleSignals.hasApk: an `.apk` file in the URL's path or query."""
    parts = urlsplit(url if "://" in url else "https://" + url)
    target = (parts.path + ("?" + parts.query if parts.query else "")).lower()
    return APK_PATTERN.search(target) is not None


def host_imitation(verdict: host_rules.HostVerdict | None) -> str:
    return {"HIGH": "certain", "MEDIUM": "suspicious"}.get(verdict.risk if verdict else "", "none")


def url_facts(url: str, brands: Brands) -> Facts:
    """The cache-and-URL stage's facts (UrlSignals.inspect)."""
    facts = Facts(blocklisted=listed_domain(url) is not None,
                  host_imitation=host_imitation(host_rules.grade_host(url)))
    if has_apk(url):
        facts = dataclasses.replace(facts, opened=True, apk_link=True,
                                    official=brands.official_site_of(ascii_host(url)) is not None)
    return facts


def page_facts(obs: PageObservation, known: Facts, brands: Brands, page_chars: int = DEFAULT_PAGE_CHARS) -> Facts:
    """`known` with the page stage's facts added (PageFacts.evidence)."""
    landing_url = (obs.final_url or obs.url) if obs.loaded else obs.url
    landing = ascii_host(landing_url)
    asks = {a for form in obs.forms for a in form.asks}
    # The form kinds as the prompt's 입력 칸 line lists them: unnamed fields only are "일반".
    kinds = (asks or {"일반"}) if obs.forms else set()
    body = obs.text[:page_chars] if obs.loaded else ""
    return dataclasses.replace(
        known,
        host_imitation=host_imitation(host_rules.grade_case(obs.url, landing_url if obs.loaded else None)),
        opened=obs.loaded and (obs.status or 0) < 400,
        official=obs.loaded and brands.official_site_of(landing) is not None,
        apk_link=bool(obs.apk_links),
        sensitive_field=bool(asks & SENSITIVE_ASKS),
        personal_field=bool(asks & PERSONAL_ASKS) or form_worded_for_contact(kinds, body),
    )


def _squash(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def claim_kind(name: str | None, landing_host: str, brands: Brands) -> str:
    """The table's claim for the brand the model says the page claims to be.

    A brand counts when one of its names appears in the claimed name ("SHOPEE INDONESIA" is
    shopee), with the Korean table first, as in the prompt's brand-mention line.
    """
    if not name:
        return "none"
    squashed = _squash(name)
    host = landing_host.removeprefix("www.")
    covers = lambda domains: any(host == d or host.endswith("." + d) for d in domains)
    for brand in brands.korean:
        if any(len(_squash(a)) >= MIN_ALIAS_CHARS and _squash(a) in squashed for a in brand["aliases"]):
            return "none" if covers(brand["domains"]) else "listed_other"
    words = re.findall(r"[a-z0-9]+", name.lower())
    token = next((t for n, t in brands.text_mentions if _squash(n) in words or _squash(n) == squashed), None)
    token = token or next((t for t in brands.global_brands if t in words or t == squashed), None)
    if token is not None:
        return "none" if covers(brands.global_brands[token]) else "listed_other"
    return "unlisted"


def answer_facts(answers: dict, obs: PageObservation, known: Facts, brands: Brands) -> Facts:
    """`known` with the model's answers added: its claim as the table's claim, and the yes/no answers."""
    landing = ascii_host((obs.final_url or obs.url) if obs.loaded else obs.url)
    return dataclasses.replace(known, claim=claim_kind(answers["claims"], landing, brands),
                               sign_in=answers["sign_in"], payment=answers["payment"], investment=answers["investment"],
                               reward_pressure=answers["reward_pressure"], content_farm=answers["content_farm"])

