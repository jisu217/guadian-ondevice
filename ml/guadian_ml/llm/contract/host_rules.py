"""Grade a URL's host for brand imitation, independent of page content.

Implements the host table in README "Labeling policy":

    HIGH   the host embeds a brand's official domain under another registrable
           domain (roblox.com.mu, https-wvvw-roblox.com)
    HIGH   the registrable domain takes a brand's official name under another
           suffix (roblox.ly for roblox.com), unless the allowlist lists it:
           brands own many country domains (amazon.fr), and the allowlist of
           popular sites is how the phone tells those apart
    MEDIUM the host pairs a brand token with an account word on a non-brand
           domain (xfinityuserverification.weebly.com)
    none   a brand token alone (meritzevent.com); page content decides

Hosts are compared with hyphens removed, so `kb-star.com.evil` is read as
`kbstar.com.evil`. Domain age is deliberately not used: the phone would have to
send each visited domain to an external lookup to learn it.
Brand tokens shorter than five characters are ignored because they match
unrelated words too often (for example "toss", "kt").
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from urllib.parse import urlsplit

from guadian_ml import paths
from guadian_ml.llm.vendor import judge_agent as ja
from guadian_ml.shared import public_suffix

MIN_TOKEN_LENGTH = 5

# Global brands common in phishing feeds; the vendored BRANDS table covers Korean brands.
GLOBAL_BRANDS: dict[str, tuple[str, ...]] = {
    "roblox": ("roblox.com",),
    "microsoft": ("microsoft.com", "office.com", "live.com", "outlook.com", "microsoftonline.com"),
    "instagram": ("instagram.com",),
    "facebook": ("facebook.com", "fb.com"),
    "whatsapp": ("whatsapp.com",),
    "paypal": ("paypal.com",),
    "amazon": ("amazon.com", "amazon.co.jp", "amazon.de"),
    "xfinity": ("xfinity.com", "comcast.com"),
    "steam": ("steampowered.com", "steamcommunity.com"),
    "coinbase": ("coinbase.com",),
    "binance": ("binance.com",),
    "metamask": ("metamask.io",),
    "telegram": ("telegram.org",),
    "linkedin": ("linkedin.com",),
    "dropbox": ("dropbox.com",),
    "docusign": ("docusign.com", "docusign.net"),
    "fedex": ("fedex.com",),
}

ACCOUNT_WORDS = ("login", "logon", "signin", "verify", "verification", "account",
                 "secure", "auth", "wallet", "recover", "unlock")


RISK_ORDER_HOST = {"MEDIUM": 1, "HIGH": 2}


@dataclass(frozen=True)
class HostVerdict:
    risk: str
    type: str
    reason: str


def _brand_table() -> dict[str, tuple[str, ...]]:
    """token -> official domains, from the vendored Korean table and GLOBAL_BRANDS.

    Insertion order is deterministic (table order, tokens sorted within a brand) because
    it decides ties, and the app reads this exact order from llm_brands.json.
    """
    table: dict[str, set[str]] = {}
    for aliases, domains in ja.BRANDS:
        tokens = {d.split(".")[0] for d in domains}
        tokens |= {re.sub(r"[\s\-]", "", a.lower()) for a in aliases if a.isascii()}
        for token in sorted(tokens):
            if len(token) >= MIN_TOKEN_LENGTH:
                table.setdefault(token, set()).update(domains)
    for token, domains in GLOBAL_BRANDS.items():
        table.setdefault(token, set()).update(domains)
    return {token: tuple(sorted(domains)) for token, domains in table.items()}


_BRANDS = _brand_table()
_PSL = public_suffix.default()


def _load_allowlist() -> frozenset[str]:
    with open(paths.OFFICIAL_DOMAINS, encoding="utf-8") as f:
        return frozenset(line.strip().lower() for line in f if line.strip() and not line.startswith("#"))


_ALLOWLIST = _load_allowlist()


def _allowlisted(domain: str) -> bool:
    """DomainAllowlist.coversHost: the domain or any parent is listed."""
    labels = domain.split(".")
    return any(".".join(labels[i:]) in _ALLOWLIST for i in range(len(labels)))


def _name_of(domain: str) -> str:
    """The label before the public suffix: "amazon" for amazon.co.jp."""
    return (_PSL.registrable_domain(domain) or domain).split(".")[0]


def _under(host: str, domain: str) -> bool:
    return host == domain or host.endswith("." + domain)


def _embeds(host: str, flat: str, domain: str) -> bool:
    """True if `domain` starts at a label boundary in the host, with or without hyphens.

    The boundary keeps `olive.com` from matching `live.com`, while
    `https-wvvw-roblox.com` (boundary "-") and `kb-star.com.evil` (flattened) still match.
    """
    pattern = r"(^|[.\-])" + re.escape(domain) + r"($|[.\-/])"
    return bool(re.search(pattern, host) or re.search(pattern, flat))


def grade_host(url: str) -> HostVerdict | None:
    """Return the host-only verdict for `url`, or None when the host alone decides nothing."""
    host = (urlsplit(url).hostname or "").lower().removeprefix("www.")
    if not host:
        return None
    registrable = _PSL.registrable_domain(host) or host
    flat = host.replace("-", "")

    for token, officials in _BRANDS.items():
        if any(_under(host, d) for d in officials):
            return None   # the brand's own domain
        embedded = next((d for d in officials if _embeds(host, flat, d)), None)
        if embedded and not any(_under(registrable, d) for d in officials):
            return HostVerdict("HIGH", "impersonation",
                               f"host {host} embeds {embedded} but belongs to {registrable}")

    name = registrable.split(".")[0]
    if len(name) >= MIN_TOKEN_LENGTH and not _allowlisted(registrable):
        for officials in _BRANDS.values():
            taken = next((d for d in officials if _name_of(d) == name), None)
            if taken:
                return HostVerdict("HIGH", "impersonation",
                                   f"host {host} takes the name of {taken} on {registrable}")

    tokens = [t for t in _BRANDS if t in flat]
    if not tokens:
        return None
    token = max(tokens, key=len)
    word = next((w for w in ACCOUNT_WORDS if w in flat.replace(token, "")), None)
    if word:
        return HostVerdict("MEDIUM", "impersonation",
                           f"host {host} pairs brand '{token}' with '{word}' on {registrable}")
    return None


# Names that identify a global brand in page text. The vendored page_signals only
# looks for Korean brands, so a page titled "... - Roblox" on roblox.ly went unnoticed.
TEXT_MENTIONS: dict[str, str] = {
    "roblox": "roblox", "microsoft": "microsoft", "office 365": "microsoft", "outlook": "microsoft",
    "onedrive": "microsoft", "sharepoint": "microsoft", "instagram": "instagram", "facebook": "facebook",
    "whatsapp": "whatsapp", "paypal": "paypal", "amazon": "amazon", "xfinity": "xfinity",
    "coinbase": "coinbase", "binance": "binance", "metamask": "metamask", "telegram": "telegram",
    "linkedin": "linkedin", "dropbox": "dropbox", "docusign": "docusign", "fedex": "fedex",
}


def global_brand_mismatches(text: str, host: str) -> list[tuple[str, str]]:
    """(brand, first official domain) for global brands named in `text` whose domains do not cover `host`."""
    found: dict[str, str] = {}
    lowered = text.lower()
    for name, token in TEXT_MENTIONS.items():
        officials = GLOBAL_BRANDS[token]
        if token in found or any(_under(host, d) for d in officials):
            continue
        if re.search(r"(?<![a-z])" + re.escape(name) + r"(?![a-z])", lowered):
            found[token] = officials[0]
    return list(found.items())


def grade_case(url: str, final_url: str | None) -> HostVerdict | None:
    """Grade both the clicked URL and the page it landed on, and return the stronger verdict.

    Shorteners (tiny.cc, goo.su) hide the imitating host until the redirect lands, so
    the origin alone is not enough.
    """
    verdicts = [v for v in (grade_host(u) for u in dict.fromkeys(u for u in (url, final_url) if u)) if v]
    return max(verdicts, key=lambda v: RISK_ORDER_HOST[v.risk], default=None)
