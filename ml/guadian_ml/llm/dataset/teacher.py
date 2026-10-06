"""Label collected cases with the original GUADIAN judge (Claude).

This is an offline, development-time step. It produces teacher labels for
distilling the server judge into the on-device Qwen model; the app never calls
Claude. To make the labels mean "what the original service would have decided",
the model, criteria, prompt assembly, and call shape all match the service.

Sources:
    Criteria and output schema  GuADian23-model-compare dataset/prompt_spec.json (vendor/, hash-checked)
    Prompt assembly             GuADian23-model-compare build_prompt.py (vendor/)
    Call shape                  GuADian23-model-compare providers/claude.py: one `final_verdict` tool
                                forced with tool_choice, temperature unset as in production, no retries
    Evidence check              GuADian23-judge-research server/judge_agent.py, _ground (vendor/):
                                a HIGH verdict whose evidence is absent from the tool outputs becomes MEDIUM

The API key is read from ANTHROPIC_API_KEY, or from `.env` at the repository
root (excluded from git).

Usage:
    python -m guadian_ml.llm.dataset.teacher ml/data/llm/sources/cases_NAME.jsonl -o ml/data/llm/sources/labels_NAME.jsonl

Reruns skip cases that already have a verdict and retry cases that failed.

`--addendum` appends CRITERIA_ADDENDUM to the original criteria; each row records
its SHA256. `--only-ids PATH` labels just the case IDs listed one per line.

`--guide` labels by ml/docs/llm/labeling-guide.md instead of the original
criteria, from the student's own prompt (what the phone sees) rather than the
server's tool outputs, so the label and the model's input describe the same
observation. Its rows record the SHA256 of the guide's rules (the text the
teacher reads, from "## Principles" on), and a `HIGH` stands only on a prompt
line of the kind the guide names for its type (dataset.sft EVIDENCE_LINES);
without one it becomes `MEDIUM`.

`--questions` answers the six questions of ml/docs/llm/question-guide.md instead of
labeling a risk (docs/design.md, section 5): the verdict table sets the risk from the
answers. Its rows record the SHA256 of the guide text the teacher reads (from
"## Scope" on), and a `claims` answer whose evidence is not one of the prompt's
lines (the one its evidence quotes, else one naming the claimed organization) becomes
`null`.

`--model` picks the teacher for `--questions`; rows record it.

`--batch` sends the same requests through the Message Batches API at half the
price; results arrive within 24 hours. The batch ID is kept in OUT.batch, so an
interrupted run resumes waiting for the same batch instead of paying twice.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from collections import Counter
from datetime import datetime, timezone

import anthropic

from guadian_ml import paths
from guadian_ml.llm.contract.answer import TYPES
from guadian_ml.llm.contract.prompt import Brands, observation_from_case, user_message
from guadian_ml.llm.dataset.sft import EVIDENCE_LINES
from guadian_ml.llm.vendor import build_prompt as bp
from guadian_ml.llm.vendor import judge_agent as ja

# Production default of the original server (server/main.py JUDGE_MODEL).
MODEL = "claude-haiku-4-5"
SPEC_PATH = os.path.join(os.path.dirname(bp.__file__), "prompt_spec.json")
# Published in GuADian23-model-compare README. A mismatch means the criteria differ from production.
SPEC_SHA256 = "af5c136ed645603f52bd08d2ceb6c92bcf328d11b016baceceb9d2c087234033"
# Clarifies impersonation, which the original criteria leave open: naming a brand is
# not posing as it. Appended to the system prompt with --addendum. Changing this text
# changes CRITERIA_ADDENDUM_SHA256, which every labeled row records.
CRITERIA_ADDENDUM = (
    '\n'
    '## 사칭 판단 보완\n'
    '\n'
    '- 페이지에 브랜드 이름이 나온다는 것만으로는 사칭이 아니다. 사칭은 페이지가 그 브랜드 자체인 것처럼 행세할 때다. 그 브랜드의 이름이나 로고를 내세워 로그인, 본인 인증, 결제, 개인정보 입력을 받는 경우가 여기에 해당한다.\n'
    '- 다음은 사칭이 아니다: 결제 수단이나 통신사 요금 청구 안내("통신요금에 포함"), 소셜 로그인 버튼("네이버로 로그인", "카카오로 시작하기"), 제휴사나 협력사 표기, 기사나 광고 문구 속 기관명 언급, 공유 버튼.\n'
    '- page_signals의 brand_domain_mismatch는 "브랜드 이름이 나왔는데 도메인이 그 브랜드 것이 아니다"라는 사실만 알려 준다. 그것만으로 사칭이라고 하지 말고, 본문이 그 브랜드를 자처하는지 확인하라.\n'
    '- 자기 상호와 사업자등록번호를 밝히고 다른 브랜드를 언급만 하는 페이지는 사칭이 아니다.\n'
)
CRITERIA_ADDENDUM_SHA256 = hashlib.sha256(CRITERIA_ADDENDUM.encode()).hexdigest()
TOOL_DESCRIPTION = "최종 판정. evidence는 주어진 결과에서 그대로 옮긴 짧은 구절이어야 한다."
GUIDE_PATH = paths.ML_DIR / "docs" / "llm" / "labeling-guide.md"
GUIDE_MAX_TOKENS = 256
GUIDE_TOOL = {
    "name": "final_verdict",
    "description": "The label for this page under the guide.",
    "input_schema": {
        "type": "object",
        "properties": {
            "risk": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
            "type": {"type": "string", "enum": sorted(TYPES)},
            "evidence": {"type": "string", "description": "For HIGH, one line of the page description copied "
                                                          "exactly; otherwise an empty string."},
        },
        "required": ["risk", "type", "evidence"],
    },
}


def load_guide() -> tuple[str, str]:
    """The guide's rules (from "## Principles" on) as the teacher's system prompt, and their SHA256."""
    text = GUIDE_PATH.read_text(encoding="utf-8")
    rules = text[text.index("## Principles"):]
    system = ("You label ad landing pages for a phone app that warns people before they visit them. "
              "The user message describes one page as the phone observed it. Label it by the guide below, "
              "using only that description, and answer with the final_verdict tool.\n\n" + rules)
    return system, hashlib.sha256(rules.encode()).hexdigest()


def guide_request(case: dict, system: str, guide_sha256: str, brands: Brands):
    """Row, Messages API parameters, and grounding for one case labeled by the guide."""
    prompt = user_message(observation_from_case(case), brands)
    row = {
        "case_id": case["case_id"],
        "model": MODEL,
        "guide_sha256": guide_sha256,
        "prompt_sha256": hashlib.sha256(prompt.encode()).hexdigest(),
        "labeled_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }
    params = {
        "model": MODEL,
        "max_tokens": GUIDE_MAX_TOKENS,
        "system": system,
        "tools": [GUIDE_TOOL],
        "tool_choice": {"type": "tool", "name": "final_verdict"},
        "messages": [{"role": "user", "content": prompt}],
    }

    def ground(verdict: dict) -> dict:
        """A HIGH rests on a prompt line of the kind the guide names for its type.

        The teacher's quote is widened to the whole line containing it; when that is not
        such a line, the first one in the prompt is taken, as dataset.sft picks the
        student's evidence. Without one the answer is MEDIUM.
        """
        if verdict.get("risk") != "HIGH":
            return {**verdict, "evidence": ""}
        prefixes = EVIDENCE_LINES.get(verdict.get("type"), ())
        lines = [l.strip() for l in prompt.splitlines()]
        evidence = str(verdict.get("evidence") or "").strip()
        line = next((l for l in lines if evidence and evidence in l and l.startswith(prefixes)), None)
        line = line or next((l for prefix in prefixes for l in lines if l.startswith(prefix)), None)
        return {**verdict, "evidence": line} if line else {**verdict, "risk": "MEDIUM", "evidence": ""}

    return row, params, ground


QUESTIONS_PATH = paths.ML_DIR / "docs" / "llm" / "question-guide.md"
QUESTION_KEYS = ("sign_in", "payment", "investment", "reward_pressure", "content_farm")
QUESTIONS_TOOL = {
    "name": "page_answers",
    "description": "Answers to the six questions of the guide for this page.",
    "input_schema": {
        "type": "object",
        "properties": {
            "claims": {"type": ["string", "null"], "description": "The organization the page claims to be, or null."},
            "evidence": {"type": "string", "description": "For a claim, one line of the page description copied "
                                                          "exactly; otherwise an empty string."},
            **{key: {"type": "boolean"} for key in QUESTION_KEYS},
        },
        "required": ["claims", "evidence", *QUESTION_KEYS],
    },
}


# Room for a newer teacher's thinking before its JSON answer.
QUESTIONS_MAX_TOKENS = 8000


def load_questions() -> tuple[str, str]:
    """The question guide (from "## Scope" on) as the teacher's system prompt, and its SHA256."""
    text = QUESTIONS_PATH.read_text(encoding="utf-8")
    rules = text[text.index("## Scope"):]
    system = ("You read descriptions of ad landing pages for a phone app that warns people before they visit "
              "them. The user message describes one page as the phone observed it. Answer the six questions of "
              "the guide below about it, using only that description, with the page_answers tool.\n\n" + rules)
    return system, hashlib.sha256(rules.encode()).hexdigest()


def question_request(case: dict, system: str, guide_sha256: str, brands: Brands, model: str = MODEL):
    """Row, Messages API parameters, and grounding for one case answered by the question guide."""
    prompt = user_message(observation_from_case(case), brands)
    row = {
        "case_id": case["case_id"],
        "model": model,
        "guide_sha256": guide_sha256,
        "prompt_sha256": hashlib.sha256(prompt.encode()).hexdigest(),
        "labeled_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }
    params = {"model": model, "system": system, "messages": [{"role": "user", "content": prompt}]}
    if model == MODEL:
        params.update(max_tokens=GUIDE_MAX_TOKENS, tools=[QUESTIONS_TOOL],
                      tool_choice={"type": "tool", "name": QUESTIONS_TOOL["name"]})
    else:
        # Newer models refuse forced tool use; structured output returns the same object as JSON
        # text, and leaves room for their thinking before it.
        schema = {**QUESTIONS_TOOL["input_schema"], "additionalProperties": False}
        schema["properties"] = {**schema["properties"], "claims": {"type": "string", "description":
                                "The organization the page claims to be, or an empty string."}}
        params.update(max_tokens=QUESTIONS_MAX_TOKENS, output_config={"format": {"type": "json_schema", "schema": schema}})

    def ground(answers: dict) -> dict:
        """A claim rests on a whole prompt line: the one its quoted evidence comes from, with or
        without the line's label, else the first line naming the claimed organization; without
        one it is null."""
        grounded = {key: bool(answers.get(key)) for key in QUESTION_KEYS}
        claims = str(answers.get("claims") or "").strip() or None
        quote = str(answers.get("evidence") or "").strip()
        quote = quote.split(": ", 1)[1] if ": " in quote and quote.index(": ") < 20 else quote
        lines = [l.strip() for l in prompt.splitlines()]
        squash = lambda text: re.sub(r"\s+", "", text).lower()
        line = None
        if claims:
            line = next((l for l in lines if quote and quote in l), None) or \
                next((l for l in lines if squash(claims) in squash(l)), None)
        return {"claims": claims if line else None, "evidence": line or "", **grounded}

    return row, params, ground


def read_api_key() -> str:
    key = os.environ.get("ANTHROPIC_API_KEY")
    env_path = os.path.join(paths.REPO_DIR, ".env")
    if not key and os.path.exists(env_path):
        with open(env_path, encoding="utf-8") as f:
            for line in f:
                name, _, value = line.strip().partition("=")
                if name == "ANTHROPIC_API_KEY":
                    key = value.strip().strip('"').strip("'")
    if not key:
        sys.exit("ANTHROPIC_API_KEY is not set (environment or repository-root .env)")
    return key


def load_verified_spec() -> dict:
    with open(SPEC_PATH, "rb") as f:
        if hashlib.sha256(f.read()).hexdigest() != SPEC_SHA256:
            sys.exit(f"{SPEC_PATH} does not match the published hash; labels would not reflect production criteria")
    return bp.load_spec(SPEC_PATH)


def labeled_case_ids(path: str) -> set[str]:
    """Case IDs that already have a verdict. Failed rows are not counted, so they are retried."""
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return {row["case_id"] for row in map(json.loads, filter(str.strip, f)) if "error" not in row}


def request(case: dict, spec: dict, tool: dict, addendum: str) -> tuple[dict, dict]:
    """The output row's provenance fields and the Messages API parameters for one case."""
    system, user = bp.build_messages(case, spec)
    system += addendum
    row = {
        "case_id": case["case_id"],
        "model": MODEL,
        "spec_id": spec["spec_id"],
        "addendum_sha256": CRITERIA_ADDENDUM_SHA256 if addendum else None,
        # Lets a later run prove it sent the same prompt.
        "prompt_sha256": hashlib.sha256(user.encode()).hexdigest(),
        "labeled_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }
    params = {
        "model": MODEL,
        "max_tokens": spec["params"]["max_tokens"],
        "system": system,
        "tools": [tool],
        "tool_choice": {"type": "tool", "name": "final_verdict"},
        "messages": [{"role": "user", "content": user}],
    }
    tool_texts = [item["output"] for item in case["input"]["tool_outputs"]]
    return row, params, lambda verdict: ja._ground(verdict, tool_texts)


def finish_row(row: dict, response, ground) -> None:
    """Record usage and the grounded verdict from one response; every mode forces its one tool."""
    row["usage"] = {"input_tokens": response.usage.input_tokens, "output_tokens": response.usage.output_tokens}
    verdict = next((dict(block.input) for block in response.content
                    if getattr(block, "type", "") == "tool_use"), None)
    if verdict is None:   # structured output: one text block of JSON
        text = next((block.text for block in response.content if getattr(block, "type", "") == "text"), None)
        verdict = json.loads(text) if text and response.stop_reason == "end_turn" else None
    if verdict is None:
        row["error"] = f"no answer (stop_reason={response.stop_reason})"
    else:
        row["verdict_raw"] = verdict
        row["verdict"] = ground(verdict)


def write_row(out, row: dict) -> None:
    out.write(json.dumps(row, ensure_ascii=False) + "\n")
    out.flush()


def run_batch(client: anthropic.Anthropic, cases: list[dict], build, out_path: str, poll_s: int = 60) -> int:
    """Label cases through one Message Batch; the batch ID in OUT.batch makes a rerun resume it."""
    state_path = out_path + ".batch"
    prepared = {c["case_id"]: build(c) for c in cases}
    if os.path.exists(state_path):
        with open(state_path, encoding="utf-8") as f:
            batch_id = json.load(f)["batch_id"]
        print(f"resuming batch {batch_id}", flush=True)
    else:
        if not prepared:
            print("nothing to label")
            return 0
        batch = client.messages.batches.create(
            requests=[{"custom_id": case_id, "params": params} for case_id, (_, params, _) in prepared.items()])
        batch_id = batch.id
        with open(state_path, "w", encoding="utf-8") as f:
            json.dump({"batch_id": batch_id, "requests": len(prepared)}, f)
        print(f"submitted batch {batch_id} with {len(prepared)} requests", flush=True)

    while (batch := client.messages.batches.retrieve(batch_id)).processing_status != "ended":
        counts = batch.request_counts
        print(f"{batch.processing_status}: {counts.processing} processing, {counts.succeeded} succeeded, "
              f"{counts.errored} errored", flush=True)
        time.sleep(poll_s)

    written = Counter()
    with open(out_path, "a", encoding="utf-8") as out:
        for result in client.messages.batches.results(batch_id):
            if result.custom_id not in prepared:
                continue   # already labeled by an earlier, partly written run
            row, _, ground = prepared[result.custom_id]
            row["batch_id"] = batch_id
            if result.result.type == "succeeded":
                finish_row(row, result.result.message, ground)
            elif result.result.type == "errored":
                row["error"] = f"batch {result.result.error.type}"
            else:
                row["error"] = f"batch {result.result.type}"
            write_row(out, row)
            written["error" if "error" in row else row["verdict"].get("risk", "answered")] += 1
    os.remove(state_path)
    print(f"wrote {sum(written.values())} rows: {dict(written)}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Label collected cases with the original Claude judge.")
    parser.add_argument("cases", help="cases JSONL from guadian_ml.llm.dataset.collect")
    parser.add_argument("-o", "--out", required=True, help="labels JSONL (appended to on rerun)")
    parser.add_argument("--limit", type=int, help="process only the first N cases")
    parser.add_argument("--sleep", type=float, default=0.0, help="seconds to wait between calls")
    parser.add_argument("--addendum", action="store_true", help="append CRITERIA_ADDENDUM to the criteria")
    parser.add_argument("--only-ids", help="file with one case ID per line to label")
    parser.add_argument("--batch", action="store_true", help="use the Message Batches API (half price, up to 24 h)")
    parser.add_argument("--guide", action="store_true", help="label by the labeling guide from the student's prompt")
    parser.add_argument("--questions", action="store_true", help="answer the question guide's six questions")
    parser.add_argument("--model", default=MODEL, help=f"teacher model for --questions (default {MODEL})")
    args = parser.parse_args()

    spec = load_verified_spec()
    tool = {"name": "final_verdict", "description": TOOL_DESCRIPTION, "input_schema": spec["output_schema"]}
    client = anthropic.Anthropic(api_key=read_api_key(), max_retries=0)

    done = labeled_case_ids(args.out)
    with open(args.cases, encoding="utf-8") as f:
        cases = [json.loads(line) for line in f if line.strip()]
    if args.only_ids:
        with open(args.only_ids, encoding="utf-8") as f:
            wanted = {line.strip() for line in f if line.strip()}
        cases = [c for c in cases if c["case_id"] in wanted]
    if args.limit:
        cases = cases[: args.limit]
    addendum = CRITERIA_ADDENDUM if args.addendum else ""

    todo = [c for c in cases if c["case_id"] not in done]
    if args.questions:
        system, guide_sha256 = load_questions()
        brands = Brands.load()
        build = lambda c: question_request(c, system, guide_sha256, brands, args.model)
    elif args.guide:
        system, guide_sha256 = load_guide()
        brands = Brands.load()
        build = lambda c: guide_request(c, system, guide_sha256, brands)
    else:
        build = lambda c: request(c, spec, tool, addendum)
    if args.batch:
        return run_batch(client, todo, build, args.out)
    with open(args.out, "a", encoding="utf-8") as out:
        for i, case in enumerate(todo, 1):
            row, params, ground = build(case)
            started = time.monotonic()
            try:
                finish_row(row, client.messages.create(**params), ground)
            except anthropic.APIError as e:
                row["error"] = f"{type(e).__name__}: {str(e)[:200]}"
            write_row(out, row)
            verdict = row.get("verdict") or {}
            summary = verdict.get("risk", row.get("error")) if "risk" in verdict or not verdict else \
                " ".join(k for k, v in verdict.items() if v and k != "evidence")
            print(f"[{i}/{len(todo)}] {case['case_id']} {summary} "
                  f"{verdict.get('type', '')} ({time.monotonic() - started:.1f}s)", flush=True)
            if args.sleep:
                time.sleep(args.sleep)
    return 0


if __name__ == "__main__":
    sys.exit(main())
