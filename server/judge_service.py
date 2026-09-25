import json
import os
import re
from dataclasses import asdict, dataclass
from enum import StrEnum
from typing import Any


class Risk(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    UNKNOWN = "UNKNOWN"


@dataclass
class Verdict:
    risk: Risk
    reason: str
    advice: str
    type: str = "none"
    site: str | None = None

    def to_dict(self) -> dict[str, Any]:
        out = asdict(self)
        out["risk"] = self.risk.value
        return out


_UNSET = object()


class JudgeService:
    def __init__(
        self,
        api_key: str | None | object = _UNSET,
        model: str | None = None,
    ) -> None:
        self.api_key = os.getenv("ANTHROPIC_API_KEY") if api_key is _UNSET else api_key
        self.model = model or os.getenv("ANTHROPIC_MODEL", "claude-haiku-4-5")

    def judge(self, url: str) -> Verdict:
        local = self._local_precheck(url)
        if local is not None:
            return local

        if not self.api_key:
            return Verdict(
                risk=Risk.UNKNOWN,
                reason="Remote judge is not configured.",
                advice="Keep the user in the safe confirmation flow.",
                type="remote_not_configured",
            )

        try:
            return self._ask_claude(url)
        except Exception as exc:
            return Verdict(
                risk=Risk.UNKNOWN,
                reason=f"Remote judge failed: {exc.__class__.__name__}.",
                advice="Keep the user in the safe confirmation flow.",
                type="remote_error",
            )

    def _local_precheck(self, url: str) -> Verdict | None:
        normalized = url.lower()
        if ".apk" in normalized:
            return Verdict(
                risk=Risk.HIGH,
                reason="The URL appears to download an APK file outside a trusted app store.",
                advice="Block the navigation before installation.",
                type="apk",
            )

        # Whole-token match, like the app's URL features: a substring match
        # would also flag hosts such as "prevent.com" or "freedom.org".
        suspicious_tokens = {"free", "gift", "event", "loan", "verify", "account"}
        if suspicious_tokens & set(re.split(r"[^a-z0-9]+", normalized)):
            return Verdict(
                risk=Risk.MEDIUM,
                reason="The URL contains a suspicious landing-page keyword.",
                advice="Ask the user to confirm before opening.",
                type="suspicious_url",
            )

        return None

    def _ask_claude(self, url: str) -> Verdict:
        from anthropic import Anthropic

        client = Anthropic(api_key=self.api_key)
        message = client.messages.create(
            model=self.model,
            max_tokens=450,
            temperature=0,
            system=SYSTEM_PROMPT,
            messages=[
                {
                    "role": "user",
                    "content": (
                        "Judge the risk of this advertisement landing URL. "
                        "Return only JSON.\n"
                        f"URL: {url}"
                    ),
                }
            ],
        )
        text = "".join(
            block.text for block in message.content
            if getattr(block, "type", None) == "text"
        )
        return parse_verdict_json(text)


def parse_verdict_json(text: str) -> Verdict:
    data = json.loads(extract_json(text))
    risk = Risk(data.get("risk", "UNKNOWN"))
    return Verdict(
        risk=risk,
        reason=str(data.get("reason") or "Remote judge did not provide a reason."),
        advice=str(data.get("advice") or "Keep the user in the safe confirmation flow."),
        type=str(data.get("type") or "none"),
        site=data.get("site") if data.get("site") else None,
    )


def extract_json(text: str) -> str:
    stripped = text.strip()
    if stripped.startswith("{") and stripped.endswith("}"):
        return stripped

    start = stripped.find("{")
    end = stripped.rfind("}")
    if start == -1 or end == -1 or end <= start:
        raise ValueError("Claude response did not contain JSON.")
    return stripped[start:end + 1]


SYSTEM_PROMPT = """
You are the remote fallback judge for an on-device-first hybrid ad safety system.

Return a compact JSON object only:
{
  "risk": "LOW" | "MEDIUM" | "HIGH" | "UNKNOWN",
  "reason": "short user-facing reason",
  "advice": "short user-facing advice",
  "type": "none | impersonation | credentials | apk | personal_info | payment | urgency | investment | contentfarm | unverifiable",
  "site": "optional final site or domain"
}

Use HIGH only when the risk is clear and serious, such as brand impersonation,
credential/card/ID theft, or APK installation outside a trusted store.
Use MEDIUM for uncertain but suspicious flows, personal information collection,
unverifiable pages, exaggerated rewards, investment lures, or unclear payment flows.
Use LOW only when no meaningful risk signal is visible from the URL context.
Use UNKNOWN when there is not enough information to judge.
""".strip()
