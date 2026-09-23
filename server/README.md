# Remote Fallback Server

> FastAPI fallback server for uncertain local verdicts.

`server/`는 Android `RemoteJudge`가 호출하는 `/judge` API를 제공한다. 로컬 판정이 확정되지 않은 URL만 remote fallback 대상으로 보낸다.

## Files

- `main.py` — FastAPI app, `/health`, `/judge`
- `judge_service.py` — precheck, Claude call, response parsing
- `requirements.txt` — Python dependencies
- `.env.example` — local environment template
- `test_main.py` — endpoint tests
- `test_judge_service.py` — service tests

## Environment

| Name | Required | Description |
| :-- | :-- | :-- |
| `ANTHROPIC_API_KEY` | Optional | Claude fallback API key. Missing key returns `UNKNOWN / remote_not_configured` |
| `ANTHROPIC_MODEL` | Optional | Claude model name. Default: `claude-3-5-haiku-latest` |

`.env`는 로컬 실행 전용이며 Git에 커밋하지 않는다.

## Local Run

```bash
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
cp server/.env.example server/.env
cd server
../.venv/bin/uvicorn main:app --reload
```

## Checks

- Health — `curl http://127.0.0.1:8000/health`
- Tests — `../.venv/bin/python -m unittest discover .`

Judge request:

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

## Azure Prep

Azure에서는 `.env` 파일을 업로드하지 않는다. App Service 환경변수에 값을 등록한다.

- `ANTHROPIC_API_KEY` — 실제 API key
- `ANTHROPIC_MODEL` — 사용할 Claude 모델명

권장 시작 명령:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

배포 후 확인:

```text
1. /health
2. /judge
3. Android RemoteJudge baseUrl
4. local UNKNOWN일 때만 remote fallback 호출
```

현재 Android 기본 remote URL:

```text
https://adalert-judge.azurewebsites.net
```
