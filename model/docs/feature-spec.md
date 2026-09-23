# Feature Specification

> Deterministic URL features shared by Python training and Android runtime.

feature set은 작고, 빠르고, 재현 가능해야 한다. 네트워크 요청이나 서버 전용 context는 사용하지 않는다.

## Runtime Contract

- **Feature order** — Python과 Android가 동일해야 함
- **Scaling** — 학습 산출물의 min/max 기준 사용
- **Token lists** — training/runtime 간 동기화
- **Missing values** — 기본값은 `0.0`
- **URL parsing** — scheme이 없으면 parsing용으로 `https://` 추가

## Features

| Feature | Type | Description |
| :-- | :-- | :-- |
| `url_length` | numeric | 전체 URL 길이 |
| `host_length` | numeric | host 길이 |
| `path_length` | numeric | path 길이 |
| `query_length` | numeric | query 길이 |
| `digit_ratio` | numeric | 전체 URL 중 숫자 비율 |
| `special_char_ratio` | numeric | 전체 URL 중 non-alphanumeric 비율 |
| `subdomain_count` | numeric | registrable domain 앞 label 수 근사 |
| `has_apk` | binary | APK token 또는 `.apk` suffix |
| `has_login` | binary | login/auth/account token |
| `has_event` | binary | event/promo/reward token |
| `has_free` | binary | free/gift/prize token |
| `has_shortener_token` | binary | 단축 URL/redirect 형태 |
| `brand_keyword_present` | binary | known brand keyword 포함 |
| `official_domain_match` | binary | known official domain 매칭 |
| `suspicious_tld` | binary | local policy의 의심 TLD |

## Derived Signals

- **External APK lure** — `has_apk`, `official_domain_match`
- **Brand impersonation** — `brand_keyword_present`, `official_domain_match`, `subdomain_count`
- **Reward or urgency lure** — `has_event`, `has_free`, `url_length`
- **Obfuscated URL** — `digit_ratio`, `special_char_ratio`, `query_length`

## Layer Boundary

- Official domain allowlist → Rule Engine
- Clear APK download → Rule Engine
- Brand impersonation → Rule Engine or model validation
- Ambiguous promotion URL → On-device Model
- No meaningful signal → Local RAG or Remote Fallback

## Update Rule

feature set을 바꾸면 아래를 함께 수정한다.

- `model/training/url_features.py` — Python training extractor
- `android/.../UrlFeatureExtractor.kt` — Android runtime extractor
- `model/artifacts/url_risk_centroid_model.json` — trained model artifact
