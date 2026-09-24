from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from judge_service import JudgeService


load_dotenv()

app = FastAPI(title="GUADIAN Remote Judge")
service = JudgeService()


class JudgeRequest(BaseModel):
    url: str = Field(min_length=1)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/judge")
def judge(request: JudgeRequest):
    verdict = service.judge(request.url)
    return JSONResponse(verdict.to_dict())
