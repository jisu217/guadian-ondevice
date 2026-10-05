"""How often one run's GGUF files give the same risk on the same case.

Quantization changes the weights slightly. A model that is sure of an answer
gives it at every precision; a case whose answer flips between f16, Q8, Q5,
and Q4 sits on the model's decision boundary. The pairwise rates show how far
each quantization moves the model from f16, which accuracy alone can hide.

Risk is read as the notebook's scoring reads it (an unparseable reply counts as
MEDIUM), so replies are compared without the prompts they answered.

Usage:
    python -m guadian_ml.llm.evaluation.agreement run2 [--split test]

Reads data/llm/eval/eval_<run>-*_<split>_predictions.jsonl (written by
evaluation.gguf) and writes reports/llm/<run>-agreement_<split>.json.
"""

from __future__ import annotations

import argparse
import itertools
import json
import sys

from guadian_ml import paths
from guadian_ml.llm.training import notebook as nb


def risk_reader():
    ns: dict = {}
    exec(nb.CONFIG, ns)
    exec(nb.SCORING, ns)
    return lambda reply: (ns["parse_answer"](reply) or {"risk": "MEDIUM"})["risk"]


def agreement(answers: dict[str, dict[str, str]]) -> dict:
    """answers: variant -> case_id -> risk. Only cases every variant answered are compared."""
    variants = sorted(answers)
    cases = sorted(set.intersection(*(set(a) for a in answers.values())))
    same = lambda group: sum(len({answers[v][c] for v in group}) == 1 for c in cases) / len(cases)
    return {
        "variants": variants,
        "cases": len(cases),
        "all_agree": same(variants),
        "pairwise": {f"{a}|{b}": same((a, b)) for a, b in itertools.combinations(variants, 2)},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Measure answer agreement across one run's GGUF files.")
    parser.add_argument("run", help="run name prefix, e.g. run2")
    parser.add_argument("--split", default="test", choices=("validation", "test"))
    args = parser.parse_args()

    read = risk_reader()
    answers: dict[str, dict[str, str]] = {}
    for path in sorted(paths.LLM_EVAL.glob(f"eval_{args.run}-*_{args.split}_predictions.jsonl")):
        variant = path.name.removeprefix(f"eval_{args.run}-").removesuffix(f"_{args.split}_predictions.jsonl")
        if variant == "colab":   # the notebook's own predictions: another format, not a GGUF file
            continue
        with open(path, encoding="utf-8") as f:
            answers[variant] = {r["case_id"]: read(r["reply"]) for r in map(json.loads, f) if r}
    if len(answers) < 2:
        print(f"need at least two prediction files for {args.run}, found {sorted(answers)}", file=sys.stderr)
        return 1

    record = {"run": args.run, "split": args.split, **agreement(answers)}
    out = paths.LLM_REPORTS / f"{args.run}-agreement_{args.split}.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(record, f, indent=2)
        f.write("\n")
    print(json.dumps(record, indent=2))
    print("wrote", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
