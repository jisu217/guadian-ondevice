# URL Risk Dataset Schema

This document defines the dataset format for training and evaluating the on-device URL risk classifier.

The dataset should describe URL risk without storing private user activity, access tokens, phone numbers, personal identifiers, or raw production logs.

## Record Format

Each row represents one URL sample.

| Field | Required | Description |
| :-- | :-- | :-- |
| `url` | yes | URL or landing page address to classify |
| `label` | yes | Risk label: `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN` |
| `source` | yes | Dataset source, such as `reference`, `synthetic`, `manual-review`, or `public-feed` |
| `reason` | yes | Short labeling rationale |
| `category` | no | Risk category, such as `official-domain`, `apk-download`, `brand-impersonation`, `suspicious-pattern`, or `no-signal` |
| `brand` | no | Related brand or service name when impersonation is relevant |
| `reviewed_by` | no | Reviewer identifier or `rule-policy` when labeled from deterministic rules |
| `created_at` | no | Date when the sample was added, formatted as `YYYY-MM-DD` |

Recommended CSV header:

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
```

## Label Definitions

| Label | Meaning |
| :-- | :-- |
| `LOW` | Official or trusted destination with no clear risky signal |
| `MEDIUM` | Suspicious or ambiguous destination that should require user confirmation |
| `HIGH` | Strong risk signal such as external APK download, clear impersonation, or credential/payment lure |
| `UNKNOWN` | Insufficient evidence or intentionally unlabeled sample |

## Labeling Guidelines

| Signal | Suggested Label |
| :-- | :-- |
| Official domain or allowlisted host | `LOW` |
| APK download outside trusted app stores | `HIGH` |
| Brand keyword on a non-official domain | `MEDIUM` or `HIGH` |
| Login, reward, event, or urgent action lure on an unfamiliar host | `MEDIUM` |
| Request for sensitive personal or payment information | `HIGH` |
| Shortened, redirected, or heavily parameterized URL without enough evidence | `MEDIUM` or `UNKNOWN` |
| No clear signal | `UNKNOWN` |

## Dataset Split

Use stable splits so model changes can be compared fairly.

| Split | Purpose | Suggested Ratio |
| :-- | :-- | :-- |
| `train` | Model fitting | 70% |
| `validation` | Feature/model selection | 15% |
| `test` | Final evaluation only | 15% |

Avoid placing near-duplicate URLs from the same campaign across different splits. Duplicates can make evaluation look better than the actual generalization quality.

## Privacy And Safety Rules

- Do not commit raw browsing history, production logs, API keys, session tokens, phone numbers, resident registration numbers, card numbers, or user-entered form values.
- Prefer synthetic, manually reviewed, or public-source samples.
- Redact query parameters when they contain identifiers or tokens.
- Store large or sensitive datasets outside the repository.
- Commit only small schema examples or sanitized fixtures.

## Example Rows

These are illustrative examples, not a training dataset.

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
https://example.com,LOW,synthetic,official-looking neutral domain,official-domain,,rule-policy,2026-09-22
https://promo-example-login.com/event,MEDIUM,synthetic,reward and login lure on unfamiliar host,suspicious-pattern,example,manual-review,2026-09-22
https://unknown-site.test/download/app.apk,HIGH,synthetic,external APK download,apk-download,,rule-policy,2026-09-22
```
