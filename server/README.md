# Remote Fallback Server

FastAPI fallback used when the Android local pipeline remains uncertain after
rules, model, local policy retrieval, and page-content inspection.

## Scope

Implemented:

- `/health`
- `/judge`
- optional NDJSON streaming response
- local precheck for obvious APK and suspicious-token cases
- Claude fallback when `ANTHROPIC_API_KEY` is configured
- safe `UNKNOWN` when remote fallback is unavailable

Not implemented:

- production authentication
- rate limiting
- persistent logging
- production deployment configuration

## Files

| File | Purpose |
| :-- | :-- |
| `main.py` | FastAPI app and endpoints |
| `judge_service.py` | precheck, Claude call, parsing, fallback behavior |
| `requirements.txt` | Python dependencies |
| `test_main.py` | endpoint tests |
| `test_judge_service.py` | service tests |

## Setup

```bash
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
cp server/.env.example server/.env
```

Run locally:

```bash
cd server
../.venv/bin/uvicorn main:app --reload
```

## Checks

```bash
curl http://127.0.0.1:8000/health
curl -X POST http://127.0.0.1:8000/judge \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.org/about"}'
```

Tests:

```bash
.venv/bin/python -m unittest discover server
```

## Environment

| Name | Required | Description |
| :-- | :-- | :-- |
| `ANTHROPIC_API_KEY` | no | missing key returns `UNKNOWN` with `remote_not_configured` |
| `ANTHROPIC_MODEL` | no | defaults to `claude-3-5-haiku-latest` |

Current Kotlin default remote URL:

```text
https://adalert-judge.azurewebsites.net
```
