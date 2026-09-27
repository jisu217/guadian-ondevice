"""The on-device model's input format, defined once for training and for the app.

A prompt is a pure function of a PageObservation: what page inspection can see
on the phone (addresses, load status, redirect count, title, visible text, form
fields, APK links). Both sides build the observation their own way (from a
collected case here, from a WebView in the app) and then apply the same rules,
so the model is served exactly the kind of input it was trained on.

The Kotlin port is core/llm PromptBuilder.kt. Brand data comes from
model/runtime/llm_brands.json, which both sides read; `python prompt_format.py
--export-brands` regenerates it from the vendored table and host_rules.py.
data/fixtures/llm_prompt_cases.jsonl (written by build_prompts.py) holds
observations with their expected prompts for the Kotlin parity test.

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

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from urllib.parse import urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import host_rules  # noqa: E402

BRANDS_PATH = os.path.join(os.path.dirname(HERE), "runtime", "llm_brands.json")
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
    """Rebuild what page inspection saw from a collected case (collect_cases.py output)."""
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
    """Brand data shared with the app (model/runtime/llm_brands.json)."""

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
        host = host.removeprefix("www.")
        return next((b["name"] for b in self.korean if any(_covers(host, d) for d in b["domains"])), None)

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


def export_brands() -> dict:
    """The brand data both sides read, from the vendored Korean table and host_rules.py."""
    return {
        "korean": [{"name": aliases[0], "aliases": list(aliases), "domains": list(domains)}
                   for aliases, domains in host_rules.ja.BRANDS],
        "global_brands": [[token, list(domains)] for token, domains in host_rules.GLOBAL_BRANDS.items()],
        "text_mentions": [[name, token] for name, token in host_rules.TEXT_MENTIONS.items()],
        "host_tokens": [[token, list(domains)] for token, domains in host_rules._BRANDS.items()],
        "account_words": list(host_rules.ACCOUNT_WORDS),
        "min_token_length": host_rules.MIN_TOKEN_LENGTH,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Maintain the shared brand data.")
    parser.add_argument("--export-brands", action="store_true", help=f"rewrite {BRANDS_PATH}")
    args = parser.parse_args()
    if args.export_brands:
        with open(BRANDS_PATH, "w", encoding="utf-8") as f:
            json.dump(export_brands(), f, ensure_ascii=False, indent=1)
            f.write("\n")
        print("wrote", BRANDS_PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())


FIXTURE_PATH = os.path.join(os.path.dirname(HERE), "data", "fixtures", "llm_prompt_cases.jsonl")


def fixture_observations() -> list[PageObservation]:
    """Synthetic observations that exercise every line and limit of the format.

    Synthetic so the committed fixture holds no collected page text.
    """
    long_text = "안내 " * 400
    return [
        PageObservation(url="https://unreachable.example/path", loaded=False),
        PageObservation(url="https://roblox.com.mu/games/1", loaded=False),
        PageObservation(url="https://shop.example.com/sale", loaded=True, final_url="https://shop.example.com/sale",
                        status=200, title="여름 세일", text=long_text.strip(), text_chars=4000),
        PageObservation(url="https://ad.example.net/click?id=1", loaded=True,
                        final_url="https://landing.example.org/", status=200, redirects=2,
                        title="Landing", text="상품 소개 페이지입니다.", text_chars=900),
        PageObservation(url="https://m.kbstar.com/quics", loaded=True, final_url="https://m.kbstar.com/quics",
                        status=200, title="", text="", text_chars=12),
        PageObservation(url="https://gone.example/", loaded=True, final_url="https://gone.example/",
                        status=404, title="Not Found", text="Not Found", text_chars=9),
        PageObservation(url="https://kb-star-bank.top/login", loaded=True, final_url="https://kb-star-bank.top/login",
                        status=200, title="KB국민은행 본인 인증", text="KB 국민은행 고객님 본인 인증이 필요합니다.",
                        text_chars=300,
                        forms=(FormObservation(("password", "rrn"), True),)),
        PageObservation(url="https://www.roblox.ly/users/1/profile", loaded=True,
                        final_url="https://www.roblox.ly/users/1/profile", status=200,
                        title="Player - Roblox", text="Player - Roblox Log in to Roblox", text_chars=250),
        PageObservation(url="https://form.example.com/", loaded=True, final_url="https://form.example.com/",
                        status=200, title="Newsletter", text="이메일을 입력하세요", text_chars=400,
                        forms=(FormObservation((), False), FormObservation(("phone",), False))),
        PageObservation(url="https://apk.example.com/", loaded=True, final_url="https://apk.example.com/",
                        status=200, title="Download", text="앱 설치", text_chars=300,
                        apk_links=("https://apk.example.com/" + "x" * 120 + "/app.apk",)),
        PageObservation(url="https://알뜰상조.com/?utm=1", loaded=True, final_url="https://xn--ig2bl5pwvd7zd.com/?utm=1",
                        status=200, title="상조", text="상조 서비스", text_chars=500),
        PageObservation(url="https://long.example.com/" + "a" * 250, loaded=True,
                        final_url="https://long.example.com/", status=200, title="T" * 120, text="내용", text_chars=500),
        PageObservation(url="http://www.xfinityuserverification.weebly.com/", loaded=True,
                        final_url="http://www.xfinityuserverification.weebly.com/", status=200,
                        title="Xfinity Sign In", text="Xfinity account verification", text_chars=240),
        PageObservation(url="https://m.naver.com/", loaded=True, final_url="https://m.naver.com/", status=200,
                        title="NAVER", text="네이버 메인 네이버페이", text_chars=5000),
        PageObservation(url="https://many.example/", loaded=True, final_url="https://many.example/", status=200,
                        title="제휴", text="네이버 카카오 구글 애플 삼성전자 쿠팡 roblox paypal", text_chars=600),
        PageObservation(url="https://tiny.cc/abc", loaded=True, final_url="https://www.roblox.com.bi/users/2",
                        status=200, redirects=1, title="Roblox", text="Roblox", text_chars=180),
    ]


SIGNAL_FIXTURE_PATH = os.path.join(os.path.dirname(HERE), "data", "fixtures", "llm_page_signal_cases.json")


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


def signal_fixture() -> dict:
    """Cases for core/llm PageSignals.kt, computed with the original rules."""
    field_sets = [
        [{"type": "password", "name": "pw"}],
        [{"type": "text", "name": "jumin", "placeholder": "주민등록번호"}],
        [{"type": "text", "placeholder": "주민 등록 번호 앞자리"}],
        [{"type": "text", "id": "RRN"}],
        [{"type": "text", "name": "cardNo", "placeholder": "카드 번호"}, {"type": "text", "name": "cvc"}],
        [{"type": "tel", "name": "x"}],
        [{"type": "text", "placeholder": "휴대폰 번호"}],
        [{"type": "text", "name": "hotel"}],
        [{"type": "text", "name": "tel_no"}],
        [{"type": "text", "placeholder": "계좌번호"}, {"type": "text", "autocomplete": "account-number"}],
        [{"type": "select", "name": "bank"}, {"type": "textarea", "name": "memo"}],
        [{"type": "email", "name": "email", "placeholder": "이메일"}],
        [{"type": "text", "name": "Resident_ID"}],
        [{"type": "text", "name": "tel번호"}],
        [{"type": "text", "name": "rrn_no"}],
        [{"type": "text", "placeholder": "주민\u3000등록번호"}],
        [{"type": "text", "name": "id-rrn"}],
        [{"type": "text", "name": f"f{i}", "placeholder": "phone" if i == 20 else ""} for i in range(21)],
    ]
    actions = [
        ("https://shop.example.com/a", None),
        ("https://shop.example.com/a", "https://shop.example.com/submit"),
        ("https://shop.example.com/a", "https://pay.example.com/submit"),
        ("https://www.shop.co.kr/a", "https://login.shop.co.kr/post"),
        ("https://www.shop.co.kr/a", "https://evil.co.kr/post"),
        ("https://kb-star.top/login", "https://collect.attacker.net/p.php"),
        ("https://a.b.example.org/", "https://example.org/x"),
    ]
    texts = ["  광고\n\n  안내\t\t입니다  ", "a b　c", "", "   ", "line1\r\nline2"]
    return {
        "forms": [{"fields": f, "asks": form_asks(f)} for f in field_sets],
        "actions": [{"final_url": u, "action": a, "external": external_action(u, a)} for u, a in actions],
        "texts": [{"raw": t, "collapsed": collapse_text(t)} for t in texts],
    }
