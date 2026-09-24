# Dataset Schema

This document defines the committed CSV schema for URL-risk model samples.
The dataset is designed for prototype training and evaluation, not for storing
raw browsing history or production logs.

## File

```text
model/data/sample_urls.csv
```

Header:

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
```

## Fields

| Field | Required | Description |
| :-- | :-- | :-- |
| `url` | yes | URL to classify |
| `label` | yes | `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN` |
| `source` | yes | sample origin, such as `synthetic`, `reference-derived`, `public-benign`, or `public-feed` |
| `reason` | yes | short labeling rationale |
| `category` | no | risk category, such as `official-domain`, `apk-download`, `brand-impersonation`, `suspicious-pattern`, or `no-signal` |
| `brand` | no | related brand when the sample involves possible impersonation |
| `reviewed_by` | no | reviewer or provenance marker, such as `rule-policy`, `human-review`, or `llm-consensus-claude-gemini-openai` |
| `created_at` | no | date in `YYYY-MM-DD` format |

## Labels

| Label | Meaning |
| :-- | :-- |
| `LOW` | official, trusted, or no meaningful risk signal |
| `MEDIUM` | suspicious but not clearly severe; should trigger user confirmation |
| `HIGH` | strong risk signal such as APK download, impersonation, credential capture, or payment lure |
| `UNKNOWN` | insufficient evidence or intentionally unresolved sample |

## Labeling Rules

- Official domain or allowlisted host: `LOW`
- APK download outside trusted app stores: `HIGH`
- Brand keyword on a non-official domain: `MEDIUM` or `HIGH`
- Reward, event, or login lure on unfamiliar host: usually `MEDIUM`
- Sensitive personal or payment information request: `HIGH`
- Shortened or redirected URL without enough evidence: `MEDIUM` or `UNKNOWN`
- No clear signal from the URL alone: `UNKNOWN`

## Safety Rules

- Do not commit raw user activity, browser history, or production logs.
- Redact session IDs, account identifiers, API keys, and private tokens.
- Prefer synthetic, public-benign, or reviewed reference-derived samples.
- Keep sensitive or large raw datasets outside this repository.
- Preserve label provenance when using LLM-consensus rows.

## Example

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
https://example.com,LOW,synthetic,official-looking neutral domain,official-domain,,rule-policy,2026-09-22
https://promo-example-login.com/event,MEDIUM,synthetic,reward and login lure on unfamiliar host,suspicious-pattern,example,manual-review,2026-09-22
https://unknown-site.test/download/app.apk,HIGH,synthetic,external APK download,apk-download,,rule-policy,2026-09-22
```
