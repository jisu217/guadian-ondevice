"""Read the on-device model's reply the way the app does.

Port of core/llm AnswerReader.kt, so offline scores (llm.evaluation.gguf) count
what the phone would actually show. fixtures/llm/answer_cases.jsonl, written by
guadian_ml.llm.contract.fixtures, holds replies with their expected readings
for the Kotlin parity test.

Rules:
    unreadable reply (no JSON object, risk or type outside the schema) -> uncertain MEDIUM
    HIGH whose type is not impersonation/credentials/apk, or whose evidence is not
    one of the prompt's lines verbatim -> MEDIUM
    anything else -> as answered
"""

from __future__ import annotations

import json

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
