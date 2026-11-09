"""The verdict table: the one place that maps signals and the model's answers to a risk.

Port of core/judge VerdictTable.kt; both read ml/runtime/verdict_table.json, and
fixtures/llm/verdict_cases.json, written by guadian_ml.llm.contract.fixtures,
holds facts with their expected decisions for the Kotlin parity test. The table
is described in docs/design.md, section 6.

A decision is made from `Facts`, where None means not known yet: page signals
before the page is observed, the model's answers before it has answered. The
decision is final when no value of the unknown facts could change its risk (a `HIGH`
always is); the pipeline's gates stop there. Finality is found by trying every value, so it holds
no criteria of its own.

Usage:
    python -m guadian_ml.llm.contract.verdict_table   # print the risk table as Markdown
"""

from __future__ import annotations

import dataclasses
import itertools
import json
import sys
from dataclasses import dataclass

from guadian_ml import paths

RISK_RANK = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
IDENTITIES = ("official", "impersonation", "unknown")
HOST_IMITATIONS = ("none", "suspicious", "certain")
# What the page claims to be: nothing, a brand in the brand table whose domain the landing
# host is not, or a brand the table does not list.
CLAIMS = ("none", "listed_other", "unlisted")
# Unknown facts take each of these values in turn when finality is checked.
PAGE_FACTS = ("opened", "official", "apk_link", "sensitive_field", "personal_field")
ANSWER_FACTS = ("claim", "sign_in", "payment", "investment", "reward_pressure", "content_farm")
# The values a decision is made with when a fact is not known yet: a page that opened
# and showed nothing, and a model that found nothing.
NEUTRAL = {"opened": True, "claim": "none"}
ACTION_LABELS = {
    "browse": "browsing only",
    "sign_in": "sign-in screen",
    "personal_field": "personal field",
    "sensitive_field": "resident registration or card number field",
    "apk_link": "`.apk` link",
    "payment": "payment push",
    "investment": "investment offer",
    "reward_pressure": "reward with time pressure",
    "content_farm": "content farm",
}


@dataclass(frozen=True)
class Facts:
    # URL signals, known before the page opens (gate A).
    blocklisted: bool = False
    host_imitation: str = "none"
    # Page signals (gate B).
    opened: bool | None = None
    official: bool | None = None
    apk_link: bool | None = None
    sensitive_field: bool | None = None
    personal_field: bool | None = None
    # The model's answers.
    claim: str | None = None
    sign_in: bool | None = None
    payment: bool | None = None
    investment: bool | None = None
    reward_pressure: bool | None = None
    content_farm: bool | None = None

    def to_json(self) -> dict:
        return dataclasses.asdict(self)


@dataclass(frozen=True)
class Decision:
    risk: str
    type: str
    reason: str       # "blocklisted", "unverifiable", "suspicious", or "<identity>:<action>"
    final: bool

    def to_json(self) -> dict:
        return dataclasses.asdict(self)


class VerdictTable:
    def __init__(self, table: dict):
        self.blocklisted = tuple(table["blocklisted"])
        self.unverifiable = tuple(table["unverifiable"])
        self.suspicious = tuple(table["suspicious"])
        self.actions = [(row["action"], {i: tuple(row[i]) for i in IDENTITIES}) for row in table["actions"]]

    @classmethod
    def load(cls) -> "VerdictTable":
        with open(paths.VERDICT_TABLE, encoding="utf-8") as f:
            return cls(json.load(f))

    def decide(self, facts: Facts) -> Decision:
        """The decision for what is known so far, and whether more facts could change its risk."""
        risk, type_, reason = self._decide(_neutral(facts))
        if risk == "HIGH":   # the highest grade: nothing can raise it
            return Decision(risk, type_, reason, final=True)
        risks = {self._decide(complete)[0] for complete in _completions(facts)}
        return Decision(risk, type_, reason, final=risks == {risk})

    def _decide(self, f: Facts) -> tuple[str, str, str]:
        """The table's answer for facts that are all known."""
        if f.blocklisted:
            return (*self.blocklisted, "blocklisted")
        identity = _identity(f)
        if identity == "unverifiable":
            return (*self.unverifiable, "unverifiable")
        best = None
        for action, cells in self.actions:
            if action == "browse" or getattr(f, action):
                cell = cells[identity]
                if best is None or RISK_RANK[cell[0]] > RISK_RANK[best[0]]:
                    best = (*cell, f"{identity}:{action}")
        if identity == "unknown" and f.host_imitation == "suspicious" \
                and RISK_RANK[self.suspicious[0]] > RISK_RANK[best[0]]:
            return (*self.suspicious, "suspicious")
        return best

    def markdown(self) -> str:
        """The risk table as docs/design.md shows it."""
        cell = lambda c: f"`{c[0]}`" if c[1] == "none" else f"`{c[0]} {c[1]}`"
        lines = ["| Action | Official | Impersonation | Unknown |", "| :-- | :-- | :-- | :-- |"]
        lines += [f"| {ACTION_LABELS[action]} | " + " | ".join(cell(cells[i]) for i in IDENTITIES) + " |"
                  for action, cells in self.actions]
        return "\n".join(lines)


def _identity(f: Facts) -> str:
    if f.host_imitation == "certain":
        return "impersonation"
    if not f.opened:
        return "unverifiable"
    if f.official:
        return "official"
    if f.claim == "listed_other" or (f.claim == "unlisted" and (f.sign_in or f.sensitive_field)):
        return "impersonation"
    return "unknown"


def _neutral(facts: Facts) -> Facts:
    return dataclasses.replace(facts, **{name: NEUTRAL.get(name, False)
                                         for name in PAGE_FACTS + ANSWER_FACTS if getattr(facts, name) is None})


def _completions(facts: Facts):
    unknown = [name for name in PAGE_FACTS + ANSWER_FACTS if getattr(facts, name) is None]
    choices = [CLAIMS if name == "claim" else (False, True) for name in unknown]
    for values in itertools.product(*choices):
        yield dataclasses.replace(facts, **dict(zip(unknown, values)))


def main() -> int:
    print(VerdictTable.load().markdown())
    return 0


if __name__ == "__main__":
    sys.exit(main())
