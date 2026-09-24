from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import parse_qsl, unquote, urlparse


DEFAULT_OFFICIAL_DOMAINS_PATH = Path(__file__).parents[1] / "data" / "official_domains.txt"

FEATURE_NAMES = [
    "url_length",
    "host_length",
    "path_length",
    "query_length",
    "digit_ratio",
    "special_char_ratio",
    "subdomain_count",
    "has_apk",
    "has_login",
    "has_event",
    "has_free",
    "has_shortener_token",
    "brand_keyword_present",
    "official_domain_match",
    "suspicious_tld",
    "query_param_count",
    "utm_param_count",
    "tracking_param_count",
    "has_nested_url",
    "has_encoded_url",
    "has_ad_click_host",
    "has_base64_like_token",
]

APK_TOKENS = {"apk", "download", "install"}
LOGIN_TOKENS = {"login", "signin", "auth", "account", "verify", "verification"}
EVENT_TOKENS = {"event", "promo", "promotion", "reward", "coupon"}
FREE_TOKENS = {"free", "gift", "prize", "win", "winner"}
SHORTENER_HOSTS = {"bit.ly", "tinyurl.com", "t.co", "short.test"}
SHORTENER_TOKENS = {"short", "go", "r", "l"}
BRAND_KEYWORDS = {"bank", "kakao", "naver", "google", "samsung", "sk", "toss"}
SUSPICIOUS_TLDS = {"zip", "top", "xyz", "click", "test"}
TRACKING_PARAM_KEYS = {
    "gclid",
    "dclid",
    "fbclid",
    "msclkid",
    "gad_source",
    "gad_campaignid",
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_content",
    "utm_term",
}
NESTED_URL_PARAM_KEYS = {"url", "u", "target", "redirect", "redirect_url", "landing_url", "dest", "destination"}
AD_CLICK_HOST_TOKENS = {
    "ad",
    "ads",
    "adclick",
    "adservice",
    "adn",
    "adscope",
    "appier",
    "criteo",
    "dable",
    "doubleclick",
    "googlesyndication",
    "haimlabs",
    "mobon",
    "outbrain",
    "taboola",
    "widerplanet",
}
BASE64_LIKE_PATTERN = re.compile(r"^[A-Za-z0-9_-]{32,}$")


def load_official_domains(path: Path = DEFAULT_OFFICIAL_DOMAINS_PATH) -> frozenset[str]:
    with path.open() as handle:
        return frozenset(line.strip().lower() for line in handle if line.strip())


OFFICIAL_DOMAINS = load_official_domains()


def is_official_domain(host: str, domains: frozenset[str] = OFFICIAL_DOMAINS) -> bool:
    """host itself, or a subdomain of, any allowlisted domain.

    A flat 50k+ domain list can only cover the exact hosts it lists (mostly
    apex domains like "naver.com"); real traffic overwhelmingly uses
    subdomains ("m.naver.com", "pay.naver.com"). Matching on suffix instead
    of exact string is what makes the allowlist actually generalize.
    """
    if not host:
        return False
    return host in domains or any(host.endswith("." + d) for d in domains)


def extract_features(url: str) -> dict[str, float]:
    normalized = _normalize_for_parse(url)
    parsed = urlparse(normalized)
    host = (parsed.hostname or "").lower()
    path = parsed.path or ""
    query = parsed.query or ""
    searchable = " ".join([host, path, query]).lower()
    tokens = set(_tokenize(searchable))
    query_pairs = parse_qsl(query, keep_blank_values=True)

    return {
        "url_length": float(len(url.strip())),
        "host_length": float(len(host)),
        "path_length": float(len(path)),
        "query_length": float(len(query)),
        "digit_ratio": _ratio(url, str.isdigit),
        "special_char_ratio": _ratio(url, lambda char: not char.isalnum()),
        "subdomain_count": float(_subdomain_count(host)),
        "has_apk": _has_apk(path, query, tokens),
        "has_login": _contains_any(tokens, LOGIN_TOKENS),
        "has_event": _contains_any(tokens, EVENT_TOKENS),
        "has_free": _contains_any(tokens, FREE_TOKENS),
        "has_shortener_token": _has_shortener_signal(host, tokens),
        "brand_keyword_present": _contains_any(tokens, BRAND_KEYWORDS),
        "official_domain_match": float(is_official_domain(host)),
        "suspicious_tld": float(_last_label(host) in SUSPICIOUS_TLDS),
        "query_param_count": float(_query_param_count(query_pairs, query)),
        "utm_param_count": float(_utm_param_count(query_pairs)),
        "tracking_param_count": float(_tracking_param_count(query_pairs)),
        "has_nested_url": _has_nested_url(query_pairs, query),
        "has_encoded_url": _has_encoded_url(query),
        "has_ad_click_host": _contains_any(tokens, AD_CLICK_HOST_TOKENS),
        "has_base64_like_token": _has_base64_like_token(tokens),
    }


def feature_vector(url: str) -> list[float]:
    features = extract_features(url)
    return [features[name] for name in FEATURE_NAMES]


def _normalize_for_parse(url: str) -> str:
    value = url.strip()
    if "://" not in value:
        return f"https://{value}"
    return value


def _tokenize(value: str) -> list[str]:
    return [token for token in re.split(r"[^a-z0-9]+", value.lower()) if token]


def _ratio(value: str, predicate) -> float:
    stripped = value.strip()
    if not stripped:
        return 0.0
    return sum(1 for char in stripped if predicate(char)) / len(stripped)


def _subdomain_count(host: str) -> int:
    labels = [label for label in host.split(".") if label]
    return max(0, len(labels) - 2)


def _contains_any(tokens: set[str], candidates: set[str]) -> float:
    return float(bool(tokens & candidates))


def _has_apk(path: str, query: str, tokens: set[str]) -> float:
    return float(path.lower().endswith(".apk") or bool(tokens & APK_TOKENS) or ".apk" in query.lower())


def _has_shortener_signal(host: str, tokens: set[str]) -> float:
    return float(host in SHORTENER_HOSTS or bool(tokens & SHORTENER_TOKENS))


def _last_label(host: str) -> str:
    labels = [label for label in host.split(".") if label]
    return labels[-1] if labels else ""


def _query_param_count(query_pairs: list[tuple[str, str]], raw_query: str) -> int:
    if query_pairs:
        return len(query_pairs)
    if not raw_query:
        return 0
    return len([part for part in re.split(r"[&;]", raw_query) if part])


def _utm_param_count(query_pairs: list[tuple[str, str]]) -> int:
    return sum(1 for key, _ in query_pairs if key.lower().startswith("utm_"))


def _tracking_param_count(query_pairs: list[tuple[str, str]]) -> int:
    return sum(1 for key, _ in query_pairs if key.lower() in TRACKING_PARAM_KEYS)


def _has_nested_url(query_pairs: list[tuple[str, str]], raw_query: str) -> float:
    for key, value in query_pairs:
        lowered_key = key.lower()
        lowered_value = value.lower()
        if lowered_key in NESTED_URL_PARAM_KEYS and _looks_like_url(lowered_value):
            return 1.0
        if _looks_like_url(lowered_value):
            return 1.0

    decoded_query = unquote(raw_query).lower()
    return float(_looks_like_url(decoded_query))


def _has_encoded_url(raw_query: str) -> float:
    lowered = raw_query.lower()
    return float("http%3a" in lowered or "https%3a" in lowered or "%2f%2f" in lowered)


def _looks_like_url(value: str) -> bool:
    return "http://" in value or "https://" in value


def _has_base64_like_token(tokens: set[str]) -> float:
    return float(any(BASE64_LIKE_PATTERN.match(token) for token in tokens))
