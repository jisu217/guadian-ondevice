# Model Specification

This document defines the committed dataset schema and the Python/Kotlin URL
feature contract for the local model artifact.

## Table of Contents

1. [Dataset](#1-dataset)
2. [Feature Contract](#2-feature-contract)
3. [Features](#3-features)
   - [3.1 Parsing and Tokenization](#31-parsing-and-tokenization)
   - [3.2 Feature Definitions](#32-feature-definitions)
   - [3.3 Token Lists](#33-token-lists)
4. [Known Limits](#4-known-limits)

## 1. Dataset

The dataset lives in `model/data/sample_urls.csv` (668 rows) with this header,
which `test_train_model.py` also asserts on the saved split files:

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
```

The "Filled" column counts non-empty values in the current file:

| Field | Required | Filled | Description |
| :-- | :-- | --: | :-- |
| `url` | yes | 668 | URL to classify |
| `label` | yes | 668 | `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN` |
| `source` | yes | 668 | `synthetic`, `reference-derived`, or `public-benign` |
| `reason` | yes | 668 | short labeling rationale |
| `category` | no | 668 | risk category, e.g. `official-domain`, `apk-download`, `brand-impersonation` |
| `brand` | no | 66 | related brand when relevant |
| `reviewed_by` | no | 668 | `rule-policy`, `manual-review`, `human-review`, or `llm-consensus-claude-gemini-openai` |
| `created_at` | no | 668 | `YYYY-MM-DD` |

The four labels are defined as follows:

| Label | Meaning |
| :-- | :-- |
| `LOW` | official, trusted, or no meaningful risk signal |
| `MEDIUM` | suspicious but not clearly severe; should trigger confirmation |
| `HIGH` | strong risk signal such as APK, credential, payment, or impersonation lure |
| `UNKNOWN` | insufficient evidence or intentionally unresolved sample |

Only sanitized fixtures are committed. Raw browsing logs, private URLs,
active-malicious feeds, and page bodies are not committed without license and
safety review; sessions, account identifiers, API keys, and private tokens are
redacted. Every row keeps whether its label came from policy, manual review,
human review, or LLM consensus.

## 2. Feature Contract

Python training and Kotlin inference must produce identical feature vectors.
The contract covers six points:

| Must agree | Python | Kotlin |
| :-- | :-- | :-- |
| Feature order | `FEATURE_NAMES` in `url_features.py` | `UrlFeatureExtractor.kt` |
| URL parsing | `urllib.parse.urlparse` | `UrlFeatureExtractor.parse()` (matches `urlparse`) |
| Token lists | `url_features.py` constants | `UrlFeatureExtractor.kt` constants |
| Missing-value defaults | empty host/path/query → `0` | same |
| Official-domain allowlist | `model/data/official_domains.txt` | same file (classpath resource) |
| Scaling parameters | written to artifact | read from artifact |

A feature change therefore touches `model/training/url_features.py`,
`app/src/main/kotlin/com/guardian/ondevice/model/UrlFeatureExtractor.kt`, the
parity fixture `model/data/feature_parity_cases.csv`, the retrained artifact
`model/artifacts/url_risk_centroid_model.json`, and the Python and Kotlin
feature tests together.

The parity fixture holds 21 URLs with expected values for all 22 features.
Python checks it in
`test_url_features.py::test_feature_parity_fixture_matches_python_extractor`,
and Kotlin in `UrlFeatureExtractorTest.kt`, which reads the fixture as a
classpath resource.

## 3. Features

### 3.1 Parsing and Tokenization

The URL is stripped and, if it has no `://`, prefixed with `https://`. It is
then parsed with `urlparse` into a lowercased host, a path, and a query. The
host, path, and query are joined and lowercased, then split on `[^a-z0-9]+`
into tokens, with empty tokens dropped. Query pairs come from
`parse_qsl(query, keep_blank_values=True)`.

Token-based binary features therefore match whole alphanumeric segments of the
host, path, and query, not substrings.

### 3.2 Feature Definitions

Rows are in vector order. Features 1–4 are `log(1 + x)`-compressed before
min-max scaling.

| # | Feature | Type | Definition |
| --: | :-- | :-- | :-- |
| 1 | `url_length` | numeric | length of trimmed URL |
| 2 | `host_length` | numeric | parsed host length |
| 3 | `path_length` | numeric | parsed path length |
| 4 | `query_length` | numeric | parsed query length |
| 5 | `digit_ratio` | numeric | digit share of trimmed URL |
| 6 | `special_char_ratio` | numeric | non-alphanumeric share of trimmed URL |
| 7 | `subdomain_count` | numeric | `max(0, host labels − 2)` |
| 8 | `has_apk` | binary | path ends with `.apk`, `.apk` in query, or APK token |
| 9 | `has_login` | binary | login token |
| 10 | `has_event` | binary | event token |
| 11 | `has_free` | binary | free token |
| 12 | `has_shortener_token` | binary | host in shortener hosts, or shortener token |
| 13 | `brand_keyword_present` | binary | brand token |
| 14 | `official_domain_match` | binary | host equals, or is a subdomain of, an allowlisted domain |
| 15 | `suspicious_tld` | binary | last host label in suspicious-TLD list |
| 16 | `query_param_count` | numeric | parsed query pairs; falls back to `&`/`;`-split parts |
| 17 | `utm_param_count` | numeric | keys starting with `utm_` |
| 18 | `tracking_param_count` | numeric | keys in tracking-parameter list |
| 19 | `has_nested_url` | binary | `http://` or `https://` in a nested-URL parameter value or in the percent-decoded query |
| 20 | `has_encoded_url` | binary | query contains `http%3a`, `https%3a`, or `%2f%2f` |
| 21 | `has_ad_click_host` | binary | ad-network token anywhere in host, path, or query |
| 22 | `has_base64_like_token` | binary | token matching `^[A-Za-z0-9_-]{32,}$` (32+ alphanumerics after tokenization) |

### 3.3 Token Lists

The token-based features match against these lists, which are identical in
Python and Kotlin:

| List | Values |
| :-- | :-- |
| APK | `apk`, `download`, `install` |
| Login | `login`, `signin`, `auth`, `account`, `verify`, `verification` |
| Event | `event`, `promo`, `promotion`, `reward`, `coupon` |
| Free | `free`, `gift`, `prize`, `win`, `winner` |
| Shortener hosts | `bit.ly`, `tinyurl.com`, `t.co`, `short.test` |
| Shortener tokens | `short`, `go`, `r`, `l` |
| Brand | `bank`, `kakao`, `naver`, `google`, `samsung`, `sk`, `toss` |
| Suspicious TLDs | `zip`, `top`, `xyz`, `click`, `test` |
| Tracking parameters | `gclid`, `dclid`, `fbclid`, `msclkid`, `gad_source`, `gad_campaignid`, `utm_source`, `utm_medium`, `utm_campaign`, `utm_content`, `utm_term` |
| Nested-URL parameters | `url`, `u`, `target`, `redirect`, `redirect_url`, `landing_url`, `dest`, `destination` |
| Ad-network tokens | `ad`, `ads`, `adclick`, `adservice`, `adn`, `adscope`, `appier`, `criteo`, `dable`, `doubleclick`, `googlesyndication`, `haimlabs`, `mobon`, `outbrain`, `taboola`, `widerplanet` |

## 4. Known Limits

URL-only features do not follow live redirect chains, and they cannot inspect
rendered forms, page text, JavaScript-only flows, domain age, certificates, or
network reputation. Length features are log-compressed before min-max scaling,
but `MEDIUM` and `UNKNOWN` still often need page or interaction context.
