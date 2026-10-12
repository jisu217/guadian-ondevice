"""Read the on-device model's reply the way the app does.

Port of core/llm AnswerReader.kt, so offline scores (llm.evaluation.gguf) count
what the phone would actually show. fixtures/llm/answer_cases.jsonl, written by
guadian_ml.llm.contract.fixtures, holds replies with their expected readings
for the Kotlin parity test; question_answer_cases.jsonl does the same for the question
model's answers (read_answers).

Rules:
    unreadable reply (no JSON object, risk or type outside the schema) -> uncertain MEDIUM
    HIGH whose type is not impersonation/credentials/apk, or whose evidence is not
    one of the prompt's lines verbatim -> MEDIUM
    MEDIUM personal_info on a page that shows neither a personal field nor a form
    worded to collect personal information (personal_info_unsupported) -> LOW
    anything else -> as answered
"""

from __future__ import annotations

import json

RISKS = {"LOW", "MEDIUM", "HIGH"}
TYPES = {"impersonation", "credentials", "apk", "personal_info", "payment", "urgency", "investment",
         "contentfarm", "unverifiable", "none"}
HIGH_TYPES = {"impersonation", "credentials", "apk"}
# Asks that make a form collect personal information; email alone does not (labeling guide, rules 4 and 5).
PERSONAL_ASKS = {"name", "phone", "password", "rrn", "card", "account"}
# Words that show a shown form with unnamed fields collects contact details. Without such a
# form they are only text, often a menu link, and are not ground (labeling guide, rule 5).
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
    """The prompt gives no ground for a personal-information warning (labeling guide, rule 5).

    Ground is a field asking for personal information, or a shown form with fields
    other than search or email whose page text names a contact field (CONTACT_WORDS) or asks consent to
    collect personal information (개인정보 with 수집 or 동의).
    """
    lines = prompt.splitlines()
    line = lambda key: next((l for l in lines if l.startswith(key + ": ")), "")
    forms = line("입력 칸").removeprefix("입력 칸: ").replace("(외부로 전송)", "")
    asks = {a.strip() for a in forms.split(",") if a.strip()}
    if asks & PERSONAL_ASKS:
        return False
    return not form_worded_for_contact(asks, line("본문"))


def form_worded_for_contact(kinds: set[str], body: str) -> bool:
    """A shown form with fields other than search or email whose page text names a contact
    field or asks consent to collect personal information (labeling guide, rule 5).
    `kinds` are the form kinds as the prompt's 입력 칸 line lists them."""
    worded = any(word in body for word in CONTACT_WORDS) or ("개인정보" in body and ("수집" in body or "동의" in body))
    return bool(kinds - {"search", "email"}) and worded


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


# The question model's yes/no answers (docs/design.md, section 5); `claims` and `evidence` come with them.
QUESTION_KEYS = ("sign_in", "payment", "investment", "reward_pressure", "content_farm")


def is_graded(reply: str | None) -> bool:
    """The reply grades the risk itself (run 3 and earlier) rather than answering the questions."""
    answer = first_json_object(reply) if reply else None
    return answer is not None and "risk" in answer


def read_answers(reply: str | None, prompt: str) -> dict | None:
    """The question model's answers as the app reads them, or None when the reply is unreadable.

    Every yes/no key must be a JSON boolean and `claims` a string or null. A claim whose
    evidence is not one of the prompt's lines, verbatim, is dropped.
    """
    answer = first_json_object(reply) if reply else None
    if answer is None or any(not isinstance(answer.get(key), bool) for key in QUESTION_KEYS):
        return None
    claims = answer.get("claims")
    if claims is not None and not isinstance(claims, str):
        return None
    claims = (claims or "").strip() or None
    evidence = _as_string(answer.get("evidence")).strip()
    grounded = claims is not None and bool(evidence) and any(line.strip() == evidence for line in prompt.splitlines())
    return {"claims": claims if grounded else None, "evidence": evidence if grounded else "",
            **{key: answer[key] for key in QUESTION_KEYS}}

