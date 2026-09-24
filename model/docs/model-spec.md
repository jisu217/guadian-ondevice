# Model Specification

This document defines the committed dataset schema and the Python/Kotlin URL
feature contract for the local model artifact.

## Dataset

File:

```text
model/data/sample_urls.csv
```

Required header:

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
```

| Field | Required | Description |
| :-- | :-- | :-- |
| `url` | yes | URL to classify |
| `label` | yes | `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN` |
| `source` | yes | sample origin, such as `synthetic`, `reference-derived`, or `public-benign` |
| `reason` | yes | short labeling rationale |
| `category` | no | risk category, such as `official-domain`, `apk-download`, or `brand-impersonation` |
| `brand` | no | related brand when relevant |
| `reviewed_by` | no | provenance marker such as `rule-policy`, `manual-review`, or `llm-consensus-claude-gemini-openai` |
| `created_at` | no | `YYYY-MM-DD` |

Label meanings:

| Label | Meaning |
| :-- | :-- |
| `LOW` | official, trusted, or no meaningful risk signal |
| `MEDIUM` | suspicious but not clearly severe; should trigger confirmation |
| `HIGH` | strong risk signal such as APK, credential, payment, or impersonation lure |
| `UNKNOWN` | insufficient evidence or intentionally unresolved sample |

Data rules:

- Commit sanitized fixtures only.
- Do not commit raw browsing logs, private URLs, active-malicious feeds, or page
  bodies without license and safety review.
- Redact sessions, account identifiers, API keys, and private tokens.
- Preserve whether labels came from policy, manual review, human review, or
  LLM consensus.

## Feature Contract

Python and Kotlin must agree on:

- feature order
- URL parsing behavior
- token lists
- missing-value defaults
- official-domain allowlist resource
- scaling parameters from the generated model artifact

When a feature changes, update:

- `model/training/url_features.py`
- `app/src/main/kotlin/com/guardian/ondevice/model/UrlFeatureExtractor.kt`
- `model/data/feature_parity_cases.csv`
- `model/artifacts/url_risk_centroid_model.json`
- Python and Kotlin feature tests

## Features

| Feature | Type | Description |
| :-- | :-- | :-- |
| `url_length` | numeric | full trimmed URL length |
| `host_length` | numeric | parsed host length |
| `path_length` | numeric | parsed path length |
| `query_length` | numeric | parsed query length |
| `digit_ratio` | numeric | digit share |
| `special_char_ratio` | numeric | non-alphanumeric share |
| `subdomain_count` | numeric | approximate subdomain depth |
| `has_apk` | binary | APK token or `.apk` suffix |
| `has_login` | binary | login/auth/account/verify token |
| `has_event` | binary | event/promo/reward/coupon token |
| `has_free` | binary | free/gift/prize/win token |
| `has_shortener_token` | binary | known shortener or short redirect-style token |
| `brand_keyword_present` | binary | known brand keyword in URL tokens |
| `official_domain_match` | binary | host appears in official-domain allowlist |
| `suspicious_tld` | binary | TLD appears in local suspicious-TLD policy |
| `query_param_count` | numeric | parsed query parameter count |
| `utm_param_count` | numeric | `utm_*` parameter count |
| `tracking_param_count` | numeric | known tracking parameter count |
| `has_nested_url` | binary | raw or decoded nested URL in query |
| `has_encoded_url` | binary | percent-encoded URL marker |
| `has_ad_click_host` | binary | ad-click or ad-network host/token signal |
| `has_base64_like_token` | binary | long URL-safe encoded-state token |

Known limits:

- URL-only features do not follow live redirect chains.
- URL-only features cannot inspect rendered forms, page text, JavaScript-only
  flows, domain age, certificates, or network reputation.
- Length features are log-compressed before min-max scaling, but `MEDIUM` and
  `UNKNOWN` still often need page or interaction context.
