# Feature Specification

This document defines the deterministic URL features shared by Python training
and the Kotlin local runtime.

The feature set is intentionally small, fast, and reproducible. It does not make
network requests and it does not inspect page content.

## Runtime Contract

Python and Kotlin must agree on:

- feature order
- URL parsing behavior
- token lists
- missing-value defaults
- scaling parameters from the trained artifact
- official-domain allowlist resource

When a feature changes, update all of these together:

- `model/training/url_features.py`
- `android/app/src/main/kotlin/com/guardian/ondevice/model/UrlFeatureExtractor.kt`
- `model/artifacts/url_risk_centroid_model.json`
- `model/data/feature_parity_cases.csv`
- relevant Python and Kotlin tests

## Parsing Rules

- Trim surrounding whitespace before parsing.
- If a URL has no scheme, parse it as `https://<value>`.
- Missing host, path, or query values should produce stable empty-string
  defaults instead of exceptions.
- Feature extraction should return numeric values for every feature.

## Features

| Feature | Type | Description |
| :-- | :-- | :-- |
| `url_length` | numeric | full trimmed URL length |
| `host_length` | numeric | parsed host length |
| `path_length` | numeric | parsed path length |
| `query_length` | numeric | parsed query length |
| `digit_ratio` | numeric | share of characters that are digits |
| `special_char_ratio` | numeric | share of characters that are not alphanumeric |
| `subdomain_count` | numeric | approximate number of labels before the registrable domain |
| `has_apk` | binary | APK token or `.apk` suffix signal |
| `has_login` | binary | login, signin, auth, account, verify, or verification token |
| `has_event` | binary | event, promo, promotion, reward, or coupon token |
| `has_free` | binary | free, gift, prize, win, or winner token |
| `has_shortener_token` | binary | known shortener host or short redirect-style token |
| `brand_keyword_present` | binary | known brand keyword in tokenized URL text |
| `official_domain_match` | binary | host appears in the official-domain allowlist |
| `suspicious_tld` | binary | TLD appears in the local suspicious-TLD policy |
| `query_param_count` | numeric | number of parsed query parameters |
| `utm_param_count` | numeric | number of `utm_*` query parameters |
| `tracking_param_count` | numeric | number of known tracking query parameters such as `gclid`, `dclid`, and `utm_*` |
| `has_nested_url` | binary | query contains a raw or decoded nested `http://` or `https://` URL |
| `has_encoded_url` | binary | query contains percent-encoded URL markers such as `https%3A` or `%2F%2F` |
| `has_ad_click_host` | binary | host/token signal for known ad-click or ad-network infrastructure |
| `has_base64_like_token` | binary | long URL-safe token that often indicates encoded redirect state |

## Derived Signals

| Signal | Main features |
| :-- | :-- |
| External APK lure | `has_apk`, `official_domain_match` |
| Brand impersonation | `brand_keyword_present`, `official_domain_match`, `subdomain_count` |
| Reward or urgency lure | `has_event`, `has_free`, `url_length` |
| Obfuscated or tracking-heavy URL | `digit_ratio`, `special_char_ratio`, `query_length` |
| Redirect/ad-network URL | `query_param_count`, `tracking_param_count`, `has_nested_url`, `has_encoded_url`, `has_ad_click_host` |
| Encoded state token | `has_base64_like_token`, `special_char_ratio`, `query_length` |

## Known Limitations

- URL-only features cannot detect rendered forms, page text, JavaScript-only
  flows, or final redirected content.
- Length features are log-compressed before min-max scaling, but URL-only
  signals still cannot inspect the final redirected page.
- `MEDIUM` and `UNKNOWN` are inherently difficult to separate without page
  context.
