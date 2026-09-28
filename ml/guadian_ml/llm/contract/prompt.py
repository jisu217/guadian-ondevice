"""The on-device model's input format, defined once for training and for the app.

A prompt is a pure function of a PageObservation: what page inspection can see
on the phone (addresses, load status, redirect count, title, visible text, form
fields, APK links). Both sides build the observation their own way (from a
collected case here, from a WebView in the app) and then apply the same rules,
so the model is served exactly the kind of input it was trained on.

The Kotlin port is core/llm PromptBuilder.kt. Brand data comes from
runtime/llm_brands.json, which both sides read; guadian_ml.resources.llm_brands
regenerates it from the vendored table and host_rules.py.
fixtures/llm/prompt_cases.jsonl (written by guadian_ml.llm.dataset.sft)
holds observations with their expected prompts for the Kotlin parity test.

Lines, in order, each only when it applies:
    주소          clicked URL, first 200 characters
    도착          landing host and redirect count, when the host changed
    페이지        load status and full text length, or that it did not open
    제목          page title, first 80 characters
    공식 도메인   whether the landing host is a known brand's official domain;
                  only when the page has almost no text or returned an error
    브랜드 언급   brands named on the page whose domains do not cover the landing host
    입력 칸       what the page's forms ask for, and whether they submit elsewhere
    APK 링크      the first link to an .apk file
    주소 흉내     host_rules verdict for the clicked URL or landing page
    본문          the start of the visible text
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from urllib.parse import urlsplit

from guadian_ml import paths
from guadian_ml.llm.contract import host_rules

BRANDS_PATH = paths.LLM_BRANDS
# The system line of every example; core/llm ChatFormat.SYSTEM must match it.
SYSTEM = "광고 도착 페이지의 위험도를 판정한다. JSON 한 줄로만 답한다."
MAX_URL_CHARS = 200
MAX_TITLE_CHARS = 80
MAX_APK_CHARS = 100
MAX_BRANDS = 4
# Below this much visible text the page was drawn by script or is empty; the
# landing domain itself then becomes the main evidence.
SPARSE_TEXT_CHARS = 200
DEFAULT_PAGE_CHARS = 500
# Korean aliases shorter than this match unrelated words too often.
MIN_ALIAS_CHARS = 3


@dataclass(frozen=True)
class FormObservation:
    asks: tuple[str, ...]          # sorted subset of credentials the fields request
    external_action: bool          # the form submits to another site


@dataclass(frozen=True)
class PageObservation:
    url: str
    loaded: bool
    final_url: str | None = None
    status: int | None = None
    redirects: int = 0
    title: str = ""
    text: str = ""                 # visible text, first 2,500 characters
    text_chars: int = 0            # length of the full visible text
    forms: tuple[FormObservation, ...] = field(default_factory=tuple)
    apk_links: tuple[str, ...] = field(default_factory=tuple)

    def to_json(self) -> dict:
        return {"url": self.url, "loaded": self.loaded, "final_url": self.final_url, "status": self.status,
                "redirects": self.redirects, "title": self.title, "text": self.text,
                "text_chars": self.text_chars,
                "forms": [{"asks": list(f.asks), "external_action": f.external_action} for f in self.forms],
                "apk_links": list(self.apk_links)}


def observation_from_case(case: dict) -> PageObservation:
    """Rebuild what page inspection saw from a collected case (llm.dataset.collect output)."""
    tools: dict[str, list[dict]] = {}
    for item in case["input"]["tool_outputs"]:
        tools.setdefault(item["tool"], []).append(json.loads(item["output"]))
    fetch = tools["fetch_page"][0]
    if "error" in fetch:
        return PageObservation(url=case["url"], loaded=False)
    signals = (tools.get("page_signals") or [{}])[0]
    return PageObservation(
        url=case["url"], loaded=True, final_url=fetch.get("final_url") or case["url"],
        status=fetch.get("status"), redirects=max(0, len(fetch.get("chain") or []) - 1),
        title=signals.get("title") or "", text=fetch.get("text") or "", text_chars=fetch.get("text_chars") or 0,
        forms=tuple(FormObservation(tuple(sorted(f.get("asks") or [])), bool(f.get("external_action")))
                    for f in signals.get("forms") or []),
        apk_links=tuple(signals.get("apk_links") or ()),
    )


def ascii_host(url: str) -> str:
    host = (urlsplit(url).hostname or "").lower()
    try:
        return host.encode("idna").decode("ascii")
    except UnicodeError:
        return host


def _norm(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def _covers(host: str, domain: str) -> bool:
    return host == domain or host.endswith("." + domain)


class Brands:
    """Brand data shared with the app (ml/runtime/llm_brands.json)."""

    def __init__(self, data: dict) -> None:
        # Ordered lists rather than objects: order decides ties, and JSON object
        # order is not preserved by every reader (org.json in the app is not).
        self.korean = data["korean"]
        self.text_mentions = [tuple(pair) for pair in data["text_mentions"]]
        self.global_brands = dict((token, domains) for token, domains in data["global_brands"])

    @classmethod
    def load(cls, path: str = BRANDS_PATH) -> "Brands":
        with open(path, encoding="utf-8") as f:
            return cls(json.load(f))

    def official_brand_of(self, host: str) -> str | None:
        """The brand that owns `host`: the Korean table first, then the global brands."""
        host = host.removeprefix("www.")
        korean = next((b["name"] for b in self.korean if any(_covers(host, d) for d in b["domains"])), None)
        return korean or next((token for token, domains in self.global_brands.items()
                               if any(_covers(host, d) for d in domains)), None)

    def mismatches(self, words: str, host: str) -> list[tuple[str, str]]:
        """(brand, first official domain) for brands named in `words` that do not own `host`."""
        found: list[tuple[str, str]] = []
        normalized = _norm(words)
        for brand in self.korean:
            if any(len(_norm(a)) >= MIN_ALIAS_CHARS and _norm(a) in normalized for a in brand["aliases"]):
                if not any(_covers(host, d) for d in brand["domains"]):
                    found.append((brand["name"], brand["domains"][0]))
        lowered = words.lower()
        seen: set[str] = set()
        for name, token in self.text_mentions:
            domains = self.global_brands[token]
            if token in seen or any(_covers(host, d) for d in domains):
                continue
            if re.search(r"(?<![a-z])" + re.escape(name) + r"(?![a-z])", lowered):
                seen.add(token)
                found.append((token, domains[0]))
        return found


def user_message(obs: PageObservation, brands: Brands, page_chars: int = DEFAULT_PAGE_CHARS) -> str:
    lines = [f"주소: {obs.url[:MAX_URL_CHARS]}"]
    if not obs.loaded:
        lines.append("페이지: 열리지 않음")
        final_url = obs.url
    else:
        final_url = obs.final_url or obs.url
        final_host = ascii_host(final_url)
        if final_host and final_host != ascii_host(obs.url):
            lines.append(f"도착: {final_host} (이동 {obs.redirects}회)")
        lines.append(f"페이지: 상태 {obs.status}, 본문 {obs.text_chars}자")
        if obs.title:
            lines.append(f"제목: {obs.title[:MAX_TITLE_CHARS]}")
        if obs.text_chars < SPARSE_TEXT_CHARS or (obs.status or 0) >= 400:
            brand = brands.official_brand_of(final_host)
            lines.append(f"공식 도메인: {final_host} = {brand}" if brand else f"공식 도메인: {final_host} 표에 없음")
        named = brands.mismatches(obs.title + " " + obs.text, final_host)
        if named:
            lines.append("브랜드 언급: " + ", ".join(f"{b}(공식 {d})" for b, d in named[:MAX_BRANDS])
                         + f" / 실제 {final_host}")
        if obs.forms:
            asks = sorted({a for form in obs.forms for a in form.asks})
            external = any(form.external_action for form in obs.forms)
            lines.append(f"입력 칸: {', '.join(asks) if asks else '일반'}" + (" (외부로 전송)" if external else ""))
        if obs.apk_links:
            lines.append(f"APK 링크: {obs.apk_links[0][:MAX_APK_CHARS]}")
    host_verdict = host_rules.grade_case(obs.url, final_url if obs.loaded else None)
    if host_verdict:
        lines.append(f"주소 흉내: {host_verdict.reason}")
    if obs.loaded and obs.text:
        lines.append(f"본문: {obs.text[:page_chars]}")
    return "\n".join(lines)




def form_asks(fields: list[dict]) -> list[str]:
    """What a form's fields request; the rules of judge_agent.page_signals, applied to one form.

    Each field is {"type", "name", "id", "placeholder", "autocomplete"} as read from the DOM,
    with "type" already defaulted to the tag name.
    """
    flags = set()
    for field_ in fields[:20]:
        t = (field_.get("type") or "").lower()
        label = " ".join(filter(None, [field_.get(k) for k in ("name", "id", "placeholder", "autocomplete")]))
        if t == "password":
            flags.add("password")
        if host_rules.ja._RRN.search(label):
            flags.add("rrn")
        if host_rules.ja._CARD.search(label):
            flags.add("card")
        if host_rules.ja._PHONE.search(label) or t == "tel":
            flags.add("phone")
        if host_rules.ja._ACCOUNT.search(label):
            flags.add("account")
    return sorted(flags)


def external_action(final_url: str, resolved_action: str | None) -> bool:
    """judge_agent.page_signals: the form posts to another site (by its co.kr-aware site_of)."""
    host = host_rules.ja.host_of(final_url) or ""
    my_site = host_rules.ja.site_of(host) if host else None
    action_host = host_rules.ja.host_of(resolved_action) if resolved_action else host
    return bool(action_host and my_site and host_rules.ja.site_of(action_host) != my_site)


def collapse_text(raw: str) -> str:
    """judge_agent._text_of after joining text nodes: runs of whitespace to one space, trimmed."""
    return re.sub(r"\s+", " ", raw).strip()
