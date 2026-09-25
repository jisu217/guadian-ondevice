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
| `type` | string | risk category or runtime identifier; default `none` |
| `site` | string \| null | optional final site or domain |

The response has no `confidence` field; the client derives it (see
[Client Integration](#7-client-integration)).

## 3. Judgment Flow

`/judge` first runs a local precheck on the lowercased URL. A URL containing
the substring `.apk` returns `HIGH` with `type=apk`. Otherwise, a URL whose
alphanumeric tokens (split on `[^a-z0-9]+`) include `free`, `gift`, `event`,
`loan`, `verify`, or `account` returns `MEDIUM` with `type=suspicious_url`.
Matching whole tokens means `prevent.com` does not match `event`.

If the precheck does not match and `ANTHROPIC_API_KEY` is unset, the server
returns `UNKNOWN` with `type=remote_not_configured`. Otherwise it calls Claude
with the model from `ANTHROPIC_MODEL`, `max_tokens` 450, `temperature` 0, and
`SYSTEM_PROMPT` from `judge_service.py`, which requires JSON-only output and
defines the risk grades and allowed `type` values.

The response text is parsed directly if it is a JSON object; otherwise the span
from the first `{` to the last `}` is extracted. A missing `reason` or `advice`
is replaced with a safe default. Any exception, including a parse failure or an
invalid `risk`, returns `UNKNOWN` with `type=remote_error`.

## 4. Files

`main.py` defines the FastAPI app and endpoints, and `judge_service.py` holds
the precheck, Claude call, parsing, and fallback behavior. Dependencies
(`fastapi`, `uvicorn[standard]`, `anthropic`, `python-dotenv`) are listed with
minimum versions in `requirements.txt`, and `.env.example` is the environment template. Endpoint
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
match returns `UNKNOWN` with `type=remote_not_configured`. `ANTHROPIC_MODEL`
defaults to `claude-haiku-4-5`.

## 7. Client Integration

The Android client is
`app/src/main/kotlin/com/guardian/ondevice/judge/RemoteJudge.kt`. It posts to
`{baseUrl}/judge`, with a default base URL of
`https://adalert-judge.azurewebsites.net`, a 3-second connect timeout, and a
10-second read timeout. It sets `confidence` to `CERTAIN` when `risk` is not
`UNKNOWN` and `UNCERTAIN` otherwise. A non-2xx response, network error, or
parse error becomes a client-side `UNKNOWN` / `UNCERTAIN` verdict.
