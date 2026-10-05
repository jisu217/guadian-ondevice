"""Score a GGUF model on the test set with llama.cpp, the runtime the app will use.

The notebook scores the fine-tuned model in PyTorch before quantization. This
script scores the file that ships, so the two reports together show whether 4-bit
quantization cost accuracy. Prompts and scoring come from the notebook's own
cells (llm.training.notebook CONFIG, EXAMPLES, SCORING), so both reports are comparable.

The report's "app" section scores the same replies as the app reads them
(llm.contract.answer, a parity-tested port of AnswerReader.kt): unreadable replies
count as MEDIUM, and HIGH without an allowed type or a quoted prompt line drops to
MEDIUM. That section is what a phone would show.

The model runs on CPU by default (--gpu-layers 0) because phones run the model
on CPU too; the reported per-case latency is a rough upper bound for a phone
with a faster core, and a lower bound for most others.

Requires `llama-server` (brew install llama.cpp) and the tokenizer files in
data/llm/cache/tokenizer (tokenizer.json, tokenizer_config.json, vocab.json, merges.txt).

Usage:
    python -m guadian_ml.llm.evaluation.gguf ~/models/qwen3/guadian-qwen3-1.7b-run2-Q5_K_M.gguf --name run2-q5_k_m

Scores go to reports/llm/<name>_<split>.json (committed); per-case replies go to
data/llm/eval/eval_<name>_<split>_predictions.jsonl (git-ignored: replies quote page text).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import socket
import subprocess
import sys
import time
import urllib.request

from guadian_ml import paths
from guadian_ml.llm.contract import answer
from guadian_ml.llm.training import notebook as nb

DATA_DIR = paths.LLM_FINAL
TOKENIZER_DIR = str(paths.TOKENIZER)


def notebook_namespace(split: str, path: str) -> dict:
    """Run the notebook's CONFIG, EXAMPLES, and SCORING cells against the local tokenizer."""
    from transformers import AutoTokenizer
    with open(path, encoding="utf-8") as f:
        examples = [json.loads(line) for line in f if line.strip()]
    ns = {"data": {split: examples}, "AutoTokenizer": AutoTokenizer}
    exec(nb.CONFIG, ns)
    ns["MODEL_ID"] = TOKENIZER_DIR
    # EXAMPLES also builds training features; only prompt_text is needed here.
    exec(nb.EXAMPLES.replace("from transformers import AutoTokenizer\n", "")
         .split("features = {")[0], ns)
    exec(nb.SCORING, ns)
    return ns


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def post(url: str, payload: dict, timeout: float) -> dict:
    request = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read())


def wait_ready(base: str, server: subprocess.Popen, timeout: float = 120) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if server.poll() is not None:
            sys.exit("llama-server exited during startup; run it by hand to see the error")
        try:
            with urllib.request.urlopen(f"{base}/health", timeout=2) as response:
                if json.loads(response.read()).get("status") == "ok":
                    return
        except OSError:
            pass
        time.sleep(0.5)
    sys.exit("llama-server did not become ready")


def run_model(args, ns: dict, examples: list[dict]) -> tuple[list[str], list[float]]:
    port = free_port()
    base = f"http://127.0.0.1:{port}"
    server = subprocess.Popen(
        ["llama-server", "-m", args.model, "--port", str(port), "-c", "2048", "-np", "1",
         "-t", str(args.threads), "-ngl", str(args.gpu_layers)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    replies, latencies = [], []
    try:
        wait_ready(base, server)
        for i, example in enumerate(examples, 1):
            started = time.monotonic()
            result = post(f"{base}/completion", {
                "prompt": ns["prompt_text"](example["messages"]), "n_predict": ns["MAX_NEW_TOKENS"],
                "temperature": 0, "stop": ["<|im_end|>"], "cache_prompt": False}, timeout=300)
            latencies.append(time.monotonic() - started)
            replies.append(result["content"])
            print(f"[{i}/{len(examples)}] {example['case_id']} {latencies[-1]:.1f}s {result['content'][:60]!r}",
                  flush=True)
    finally:
        server.terminate()
        server.wait()
    return replies, latencies


def app_report(ns: dict, examples: list[dict], replies: list[str]) -> dict:
    """Scores after the app's answer reading; readings become plain risks for the notebook's scorer."""
    readings = [answer.read(r, e["messages"][1]["content"]) for e, r in zip(examples, replies)]
    report = ns["score"](examples, [json.dumps({"risk": r["risk"]}) for r in readings])
    report.pop("unparseable")
    report["malformed"] = sum(r["reason"] == "LLM_MALFORMED_OUTPUT" for r in readings)
    report["high_lowered"] = sum(r["reason"] == "LLM_UNSUPPORTED_HIGH" for r in readings)
    report["personal_info_lowered"] = sum(r["reason"] == "LLM_UNSUPPORTED_PERSONAL_INFO" for r in readings)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="Score a GGUF model on the test set with llama.cpp.")
    parser.add_argument("model", help="GGUF file")
    parser.add_argument("--name", required=True, help="label for the output files, e.g. finetuned-q4")
    parser.add_argument("--split", default="test", choices=("validation", "test"))
    parser.add_argument("--examples", help="examples JSONL to score instead of data/llm/final/sft_<split>.jsonl, "
                                           "e.g. an older build a model was trained on")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--rescore", help="predictions JSONL from an earlier run: rescore its replies without the model")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--gpu-layers", type=int, default=0)
    args = parser.parse_args()

    ns = notebook_namespace(args.split, args.examples or os.path.join(DATA_DIR, f"sft_{args.split}.jsonl"))
    examples = ns["data"][args.split][: args.limit]

    if args.rescore:
        with open(args.rescore, encoding="utf-8") as f:
            saved = {r["case_id"]: r for r in map(json.loads, filter(str.strip, f))}
        replies = [saved[e["case_id"]]["reply"] for e in examples]
        latencies = [saved[e["case_id"]].get("latency_s", 0.0) for e in examples]
    else:
        replies, latencies = run_model(args, ns, examples)

    report = ns["score"](examples, replies)
    reviewed = [(e, r) for e, r in zip(examples, replies) if e["label_source"] == "human_review"]
    if reviewed:
        report["human_review"] = ns["score"]([e for e, _ in reviewed], [r for _, r in reviewed])
    report["app"] = app_report(ns, examples, replies)
    if reviewed:
        report["app"]["human_review"] = app_report(ns, [e for e, _ in reviewed], [r for _, r in reviewed])
    ordered = sorted(latencies)
    report["latency_s"] = {"median": ordered[len(ordered) // 2], "p90": ordered[int(0.9 * (len(ordered) - 1))],
                           "max": ordered[-1], "threads": args.threads, "gpu_layers": args.gpu_layers}
    report["model"] = os.path.basename(args.model)

    examples_path = args.examples or os.path.join(DATA_DIR, f"sft_{args.split}.jsonl")
    report["examples"] = os.path.relpath(examples_path, paths.ML_DIR)
    with open(examples_path, "rb") as f:   # identifies the exact test set the numbers belong to
        report["examples_sha256"] = hashlib.sha256(f.read()).hexdigest()
    paths.LLM_REPORTS.mkdir(parents=True, exist_ok=True)
    metrics_path = paths.LLM_REPORTS / f"{args.name}_{args.split}.json"
    with open(metrics_path, "w") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
        f.write("\n")
    if args.rescore:
        print(json.dumps(report, indent=2, ensure_ascii=False))
        print("wrote", metrics_path)
        return 0
    paths.LLM_EVAL.mkdir(parents=True, exist_ok=True)
    with open(paths.LLM_EVAL / f"eval_{args.name}_{args.split}_predictions.jsonl", "w", encoding="utf-8") as f:
        for example, reply, latency in zip(examples, replies, latencies):
            f.write(json.dumps({"case_id": example["case_id"], "label_source": example["label_source"],
                                "gold": example["messages"][2]["content"], "reply": reply,
                                "latency_s": round(latency, 2)}, ensure_ascii=False) + "\n")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    print("wrote", metrics_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
