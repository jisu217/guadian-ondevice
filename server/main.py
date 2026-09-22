from collections.abc import Iterator

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from judge_service import JudgeService


load_dotenv()

app = FastAPI(title="GUADIAN Remote Judge")
service = JudgeService()


class JudgeRequest(BaseModel):
    url: str = Field(min_length=1)
    stream: bool = False


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/judge")
def judge(request: JudgeRequest):
    verdict = service.judge(request.url)
    body = verdict.to_dict()

    if request.stream:
        return StreamingResponse(
            ndjson(body),
            media_type="application/x-ndjson",
        )

    return JSONResponse(body)


def ndjson(body: dict) -> Iterator[str]:
    import json

    yield json.dumps(body, ensure_ascii=False) + "\n"
