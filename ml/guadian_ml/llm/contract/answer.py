"""Read the on-device model's reply the way the app does.

Port of core/llm AnswerReader.kt, so offline scores (llm.evaluation.gguf) count
what the phone would actually show. fixtures/llm/answer_cases.jsonl, written by
guadian_ml.llm.contract.fixtures, holds replies with their expected readings
for the Kotlin parity test.

Rules:
    unreadable reply (no JSON object, risk or type outside the schema) -> uncertain MEDIUM
    HIGH whose type is not impersonation/credentials/apk, or whose evidence is not
    one of the prompt's lines verbatim -> MEDIUM
    MEDIUM personal_info on a page that shows no personal field and gives no other
    reason to doubt it (personal_info_unsupported) -> LOW
    anything else -> as answered
"""

from __future__ import annotations

import json

RISKS = {"LOW", "MEDIUM", "HIGH"}
TYPES = {"impersonation", "credentials", "apk", "personal_info", "payment", "urgency", "investment",
         "contentfarm", "unverifiable", "none"}
HIGH_TYPES = {"impersonation", "credentials", "apk"}
# Asks that make a form collect personal information; email alone does not (labeling policy).
PERSONAL_ASKS = {"name", "phone", "password", "rrn", "card", "account"}
# Words in the page text that point to a contact form the collector could not see as a form.
CONTACT_WORDS = ("연락처", "휴대폰", "핸드폰", "전화번호")


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


def personal_info_unsupported(prompt: str) -> bool:
    """The prompt gives no ground for a personal-information warning.

    True when no form field asks for personal information, no form posts elsewhere,
    no brand mention or host imitation line is present, and the page text names no
    contact field (CONTACT_WORDS, or 개인정보 with 수집 or 동의).
    """
    lines = prompt.splitlines()
    line = lambda key: next((l for l in lines if l.startswith(key + ": ")), "")
    forms = line("입력 칸")
    if "외부로 전송" in forms or line("브랜드 언급") or line("주소 흉내"):
        return False
    asks = {a.strip() for a in forms.removeprefix("입력 칸: ").split(",")} if forms else set()
    if asks & PERSONAL_ASKS:
        return False
    body = line("본문")
    if any(word in body for word in CONTACT_WORDS) or ("개인정보" in body and ("수집" in body or "동의" in body)):
        return False
    return True


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
    if risk == "MEDIUM" and type_ == "personal_info" and personal_info_unsupported(prompt):
        return {"risk": "LOW", "certain": True, "reason": "LLM_UNSUPPORTED_PERSONAL_INFO"}
    return {"risk": risk, "certain": True, "reason": "LLM_VERDICT"}
