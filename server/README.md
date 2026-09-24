# Remote Fallback Server

FastAPI server used only when local URL judgment remains uncertain.

The local Kotlin pipeline should make the first decision attempt. This server is
the fallback layer for cases where local rules, the URL-feature model, and local
policy retrieval cannot produce a confident verdict.

## Scope

Implemented:

- `/health` endpoint
- `/judge` endpoint
- optional NDJSON streaming response
- local precheck for obvious APK and suspicious-token cases
- Claude fallback call when `ANTHROPIC_API_KEY` is configured
- safe `UNKNOWN` response when the key is missing or the remote call fails

Not implemented:

- production authentication
- rate limiting
- persistent logging
- deployed Azure App Service configuration in this repository

## Files

| File | Purpose |
| :-- | :-- |
| `main.py` | FastAPI app and request/response endpoints |
| `judge_service.py` | local precheck, Claude call, JSON parsing, fallback behavior |
| `requirements.txt` | Python dependencies |
| `.env.example` | local environment template |
| `test_main.py` | API endpoint tests |
| `test_judge_service.py` | service-level tests |

## Environment

| Name | Required | Description |
| :-- | :-- | :-- |
| `ANTHROPIC_API_KEY` | no | Claude API key. Missing key returns `UNKNOWN` with `remote_not_configured`. |
| `ANTHROPIC_MODEL` | no | Claude model name. Defaults to `claude-3-5-haiku-latest`. |

`.env` is for local development only and should not be committed.

## Local Setup

From the repository root:

```bash
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
cp server/.env.example server/.env
```

Run the server:

```bash
cd server
../.venv/bin/uvicorn main:app --reload
```

## API Checks

Health:

```bash
curl http://127.0.0.1:8000/health
```

Judge:

```bash
curl -X POST http://127.0.0.1:8000/judge \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.org/about"}'
```

Streaming NDJSON:

```bash
curl -X POST http://127.0.0.1:8000/judge \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/app.apk","stream":true}'
```

## Tests

```bash
cd server
../.venv/bin/python -m unittest discover .
```

## Deployment Notes

For Azure App Service or a similar platform:

- configure environment variables in the platform settings, not through `.env`
- set `ANTHROPIC_API_KEY` only in the server environment
- expose the FastAPI app with a command such as:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

After deployment, verify:

1. `/health` returns `{"status":"ok"}`.
2. `/judge` returns a valid verdict JSON.
3. Android/Kotlin `RemoteJudge` points to the deployed base URL.
4. The local pipeline calls remote fallback only for uncertain local cases.

Current default remote URL in the Kotlin client:

```text
https://adalert-judge.azurewebsites.net
```
