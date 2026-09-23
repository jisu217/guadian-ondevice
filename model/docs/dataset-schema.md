# Dataset Schema

> CSV schema for URL risk model training and evaluation.

데이터셋은 URL 위험 라벨을 학습하기 위한 최소 정보만 저장한다. raw browsing history, token, 개인정보, production log는 저장하지 않는다.

## Record Format

| Field | Required | Description |
| :-- | :-- | :-- |
| `url` | yes | 분류 대상 URL |
| `label` | yes | `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN` |
| `source` | yes | `synthetic`, `reference-derived`, `public-benign`, `public-feed` |
| `reason` | yes | 라벨링 근거 |
| `category` | no | `official-domain`, `apk-download`, `brand-impersonation`, `suspicious-pattern`, `no-signal` |
| `brand` | no | 사칭 판단에 관련된 브랜드명 |
| `reviewed_by` | no | 검토자 또는 `rule-policy` |
| `created_at` | no | `YYYY-MM-DD` |

Header:

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
```

## Labels

- `LOW` — 공식/신뢰 도메인 또는 명확한 위험 신호 없음
- `MEDIUM` — 의심스럽지만 사용자 확인으로 처리 가능한 흐름
- `HIGH` — APK, 사칭, credential/payment lure 등 강한 위험 신호
- `UNKNOWN` — 판정 근거 부족 또는 의도적으로 보류한 샘플

## Labeling Rules

- Official domain or allowlisted host → `LOW`
- APK download outside trusted app stores → `HIGH`
- Brand keyword on non-official domain → `MEDIUM` or `HIGH`
- Reward/event/login lure on unfamiliar host → `MEDIUM`
- Sensitive personal or payment information request → `HIGH`
- Shortened or redirected URL without evidence → `MEDIUM` or `UNKNOWN`
- No clear signal → `UNKNOWN`

## Safety Rules

- **Do not commit raw user activity** — 개인정보 보호
- **Redact tokens and identifiers** — session/API key 유출 방지
- **Prefer synthetic or reviewed samples** — 라벨 품질 유지
- **Keep sensitive datasets outside repo** — 저장소 안전성 유지

## Example

```csv
url,label,source,reason,category,brand,reviewed_by,created_at
https://example.com,LOW,synthetic,official-looking neutral domain,official-domain,,rule-policy,2026-09-22
https://promo-example-login.com/event,MEDIUM,synthetic,reward and login lure on unfamiliar host,suspicious-pattern,example,manual-review,2026-09-22
https://unknown-site.test/download/app.apk,HIGH,synthetic,external APK download,apk-download,,rule-policy,2026-09-22
```
