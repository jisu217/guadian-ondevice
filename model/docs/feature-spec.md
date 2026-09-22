# URL Feature Specification

This document defines the first baseline feature set for the lightweight on-device URL risk classifier.

The feature set should stay small, deterministic, and easy to reproduce in both Python training code and Android runtime code.

## Goals

| Goal | Description |
| :-- | :-- |
| Lightweight | Features must be cheap enough to compute on device |
| Deterministic | The same URL should produce the same feature values in training and Android |
| Explainable | Features should map to understandable URL risk signals |
| Portable | Avoid features that require network requests or server-only context |

## Input

The classifier receives a single URL string.

Before feature extraction:

1. Trim whitespace.
2. Add `https://` only when parsing requires a scheme.
3. Lowercase the host.
4. Preserve path and query text for token-based features.
5. Do not make network requests or follow redirects.

## Baseline Features

| Feature | Type | Description |
| :-- | :-- | :-- |
| `url_length` | numeric | Total URL length |
| `host_length` | numeric | Host length |
| `path_length` | numeric | Path length |
| `query_length` | numeric | Query string length |
| `digit_ratio` | numeric | Ratio of digits in the full URL |
| `special_char_ratio` | numeric | Ratio of non-alphanumeric characters in the full URL |
| `subdomain_count` | numeric | Number of host labels before the registrable domain approximation |
| `has_apk` | binary | URL contains APK-related token or `.apk` suffix |
| `has_login` | binary | URL contains login/auth/account token |
| `has_event` | binary | URL contains event/promo/reward token |
| `has_free` | binary | URL contains free/gift/prize token |
| `has_shortener_token` | binary | Host or path looks like a shortened redirect URL |
| `brand_keyword_present` | binary | Known brand keyword appears in host/path |
| `official_domain_match` | binary | Host matches a known official domain |
| `suspicious_tld` | binary | Host uses an uncommon or high-risk TLD from the local policy list |

## Derived Signals

The model can learn from raw features, but these signals are useful for debugging and rule comparison.

| Signal | Source Features |
| :-- | :-- |
| External APK lure | `has_apk`, `official_domain_match` |
| Brand impersonation | `brand_keyword_present`, `official_domain_match`, `subdomain_count` |
| Reward or urgency lure | `has_event`, `has_free`, `url_length` |
| Obfuscated URL | `digit_ratio`, `special_char_ratio`, `query_length` |

## Label Interaction

The model should not replace deterministic rules. Training should focus on URLs that are not already obvious from the rule engine.

| Case | Preferred Layer |
| :-- | :-- |
| Official domain allowlist match | Rule Engine |
| Clear APK download | Rule Engine |
| Obvious brand impersonation | Rule Engine or model validation sample |
| Ambiguous promotion or landing URL | On-device Model |
| No meaningful feature signal | Local RAG or Remote Fallback |

## Runtime Contract

The Python training extractor and Android extractor must keep the same:

- feature order
- numeric scaling
- token lists
- missing-value defaults
- URL normalization behavior

When the feature set changes, update this document before updating training or Android code.
