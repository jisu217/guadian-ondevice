# Remote Fallback Server

FastAPI fallback called by `RemoteJudge` when the Android local pipeline
(rules, model, local knowledge, page content) remains uncertain.

## Table of Contents

1. [Scope](#1-scope)
2. [API](#2-api)
3. [Judgment Flow](#3-judgment-flow)
4. [Files](#4-files)
5. [Setup and Run](#5-setup-and-run)
6. [Environment](#6-environment)
7. [Client Integration](#7-client-integration)

## 1. Scope

The server exposes `/health` and `/judge`, runs a local precheck before any
model call, and uses Claude as the fallback judge when `ANTHROPIC_API_KEY` is
set. When the key is missing or the call fails, it returns a safe `UNKNOWN`
verdict instead of fabricating a result. Authentication, rate limiting,
persistent logging, and deployment configuration are out of scope; production
hardening is tracked in
[Technical Report](../docs/technical-report.md#10-next-steps).

## 2. API

`GET /health` returns `{"status": "ok"}`. `POST /judge` takes
`{"url": "<non-empty string>"}` and returns one verdict JSON object; an empty
`url` fails request validation (`min_length=1`). The verdict has these fields:

| Field | Type | Values |
| :-- | :-- | :-- |
| `risk` | string | `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN` |
| `reason` | string | short user-facing reason |
| `advice` | string | short user-facing advice |
| `category` | string | risk category (same snake_case values as the app's `RiskCategory`); default `none` |
| `host` | string \| null | optional final host |
| `status` | string | `ok`, `not_configured` (no API key), or `error` (Claude call or parse failed) |

The response has no `confidence` field; the client derives it (see
[Client Integration](#7-client-integration)).

## 3. Judgment Flow

`/judge` first runs a local precheck (`precheck_category`) on the lowercased
path and query only; the host is ignored, so `myaccount.google.com` is not a
lure. A path/query matching `\.apk(?![a-z0-9])` returns `HIGH` with
`category=apk`. Otherwise, a path/query whose alphanumeric tokens (split on
`[^a-z0-9]+`) include `free`, `gift`, `event`, `loan`, `verify`, or `account`
returns `MEDIUM` with `category=suspicious_url`. Matching whole tokens means
`/prevention` does not match `event`. The app's `ApkRule` and
`SuspiciousUrlRule` implement the same check, and both sides are tested against
`model/data/fixtures/rule_parity_cases.csv`.

If the precheck does not match and `ANTHROPIC_API_KEY` is unset, the server
returns `UNKNOWN` with `status=not_configured`. Otherwise it calls Claude
with the model from `ANTHROPIC_MODEL`, `max_tokens` 450, `temperature` 0, and
`SYSTEM_PROMPT` from `judge_service.py`, which requires JSON-only output and
defines the risk grades and allowed `category` values.

The response text is parsed directly if it is a JSON object; otherwise the span
from the first `{` to the last `}` is extracted. A missing `reason` or `advice`
is replaced with a safe default. Any exception, including a parse failure or an
invalid `risk`, returns `UNKNOWN` with `status=error`.

## 4. Files

`main.py` defines the FastAPI app and endpoints, and `judge_service.py` holds
the precheck, Claude call, parsing, and fallback behavior. Dependencies
(`fastapi`, `uvicorn[standard]`, `anthropic`, `python-dotenv`) are pinned to
exact, tested versions in `requirements.txt`, and `.env.example` is the environment template. Endpoint
tests are in `test_main.py` and service tests in `test_judge_service.py`.

## 5. Setup and Run

From the repository root, create the environment and start the server:

```bash
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
cp server/.env.example server/.env

cd server
../.venv/bin/uvicorn main:app --reload
```

A running server can be smoke-checked with:

```bash
curl http://127.0.0.1:8000/health
curl -X POST http://127.0.0.1:8000/judge \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.org/about"}'
```

The tests also run in CI:

```bash
.venv/bin/python -m unittest discover server
```

## 6. Environment

Variables are loaded from `server/.env` via `python-dotenv`, and both are
optional. Without `ANTHROPIC_API_KEY`, every URL that the precheck does not
match returns `UNKNOWN` with `status=not_configured`. `ANTHROPIC_MODEL`
defaults to `claude-haiku-4-5`.

## 7. Client Integration

The Android client is
`core/judge/src/main/kotlin/com/guadian/ondevice/remote/RemoteJudge.kt`. It posts to
`{baseUrl}/judge`, where `baseUrl` is `BuildConfig.REMOTE_JUDGE_URL`, set with
`guadian.remoteJudgeUrl=...` in `local.properties` or
`-Pguadian.remoteJudgeUrl=...`. There is no default: with no URL configured the
client answers `UNKNOWN` without any network call. It uses a 3-second connect
timeout and a 10-second read timeout, and sends the URL through
`RemoteUrlRedactor` first (user-info and fragment dropped, query values
replaced with `_` except nested `http(s)` destinations). It sets `confidence`
to `CERTAIN` when `risk` is not `UNKNOWN` and `UNCERTAIN` otherwise, and maps
`status` to its own reason codes. A non-2xx response, network error, or parse
error becomes a client-side `UNKNOWN` / `UNCERTAIN` verdict.
