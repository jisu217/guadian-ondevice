"""Read the on-device model's reply the way the app does.

Port of core/llm AnswerReader.kt, so offline scores (evaluate_gguf.py) count what
the phone would actually show. data/fixtures/llm_answer_cases.jsonl, written by
`python answer_reader.py --write-fixture`, holds replies with their expected
readings for the Kotlin parity test.

Rules:
    unreadable reply (no JSON object, risk or type outside the schema) -> uncertain MEDIUM
    HIGH whose type is not impersonation/credentials/apk, or whose evidence is not
    one of the prompt's lines verbatim -> MEDIUM
    anything else -> as answered
"""

from __future__ import annotations

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURE_PATH = os.path.join(os.path.dirname(HERE), "data", "fixtures", "llm_answer_cases.jsonl")
RISKS = {"LOW", "MEDIUM", "HIGH"}
TYPES = {"impersonation", "credentials", "apk", "personal_info", "payment", "urgency", "investment",
         "contentfarm", "unverifiable", "none"}
HIGH_TYPES = {"impersonation", "credentials", "apk"}


def first_json_object(text: str) -> dict | None:
    """The first JSON object in `text`, parsed properly so braces inside strings are handled."""
    decoder = json.JSONDecoder()
    start = text.find("{")
    while start >= 0:
        try:
            value, _ = decoder.raw_decode(text, start)
            if isinstance(value, dict):
                return value
        except ValueError:
            pass
        start = text.find("{", start + 1)
    return None


def _as_string(value) -> str:
    # org.json optString: missing or null -> "", anything else -> its string form.
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    return value if isinstance(value, str) else str(value)


def read(reply: str | None, prompt: str) -> dict:
    """{"risk", "certain", "reason"} as the app's AnswerReader would decide."""
    answer = first_json_object(reply) if reply else None
    if answer is None:
        return {"risk": "MEDIUM", "certain": False, "reason": "LLM_MALFORMED_OUTPUT"}
    risk, type_ = _as_string(answer.get("risk")), _as_string(answer.get("type"))
    if risk not in RISKS or type_ not in TYPES:
        return {"risk": "MEDIUM", "certain": False, "reason": "LLM_MALFORMED_OUTPUT"}
    evidence = _as_string(answer.get("evidence")).strip()
    if risk == "HIGH":
        grounded = bool(evidence) and any(line.strip() == evidence for line in prompt.splitlines())
        if type_ not in HIGH_TYPES or not grounded:
            return {"risk": "MEDIUM", "certain": True, "reason": "LLM_UNSUPPORTED_HIGH"}
    return {"risk": risk, "certain": True, "reason": "LLM_VERDICT"}


def fixture_cases() -> list[dict]:
    prompt = "\n".join([
        "주소: https://kb-star-bank.top/login",
        "페이지: 상태 200, 본문 300자",
        "브랜드 언급: 국민은행(공식 kbstar.com) / 실제 kb-star-bank.top",
        "입력 칸: password, rrn (외부로 전송)",
    ])
    replies = [
        '{"risk": "HIGH", "type": "impersonation", "evidence": "브랜드 언급: 국민은행(공식 kbstar.com) / 실제 kb-star-bank.top"}',
        '{"risk": "HIGH", "type": "credentials", "evidence": "  입력 칸: password, rrn (외부로 전송)  "}',
        '{"risk": "HIGH", "type": "credentials", "evidence": ""}',
        '{"risk": "HIGH", "type": "credentials", "evidence": "본문: 없는 문장"}',
        '{"risk": "HIGH", "type": "urgency", "evidence": "입력 칸: password, rrn (외부로 전송)"}',
        '{"risk": "MEDIUM", "type": "personal_info", "evidence": ""}',
        '{"risk": "LOW", "type": "none", "evidence": ""}',
        '```json\n{"risk": "MEDIUM", "type": "payment", "evidence": "form {x}"}\n``` trailing',
        'noise {not json} then {"risk": "LOW", "type": "none"}',
        '{"risk": "medium", "type": "none"}',
        '{"risk": 2, "type": "none"}',
        '{"risk": "LOW", "type": "phishing"}',
        '{"risk": "UNKNOWN", "type": "none"}',
        '{"risk": "HIGH", "type": "apk", "evidence": "cut off',
        '{"위험도": "중"}',
        "",
        "no json at all",
    ]
    return [{"reply": r, "prompt": prompt, **read(r, prompt)} for r in replies]


def main() -> int:
    parser = argparse.ArgumentParser(description="Maintain the answer-reading parity fixture.")
    parser.add_argument("--write-fixture", action="store_true")
    args = parser.parse_args()
    if args.write_fixture:
        with open(FIXTURE_PATH, "w", encoding="utf-8") as f:
            for case in fixture_cases():
                f.write(json.dumps(case, ensure_ascii=False) + "\n")
        print("wrote", FIXTURE_PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())
