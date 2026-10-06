"""Generate notebooks/train_llm.ipynb, the Colab notebook that fine-tunes the on-device judge.

The notebook is generated rather than hand-edited so its code can be reviewed as
plain Python and the pure-Python parts (example building, scoring) can be tested
locally without a GPU; see tests/llm/test_notebook.py.

Usage:
    python -m guadian_ml.llm.training.notebook
"""

from __future__ import annotations

import nbformat

from guadian_ml import paths

OUT = paths.NOTEBOOKS / "train_llm.ipynb"
RUN = "run6"   # the training run this notebook produces; written into its cells

INTRO = """\
# LoRA fine-tuning of the on-device judge

Fine-tunes `Qwen/Qwen3-1.7B` on the compact examples from `guadian_ml.llm.dataset.sft`:
it learns to answer the six questions of `ml/docs/llm/question-guide.md` as one JSON line,
and the app's verdict table turns the answers into a risk (`docs/design.md`). It exports the merged model as an f16 GGUF. Quantize it on the Mac afterwards (Q4_K_M, Q5_K_M, Q8_0).

**Runtime:** GPU (T4 is enough). **Inputs:** `sft_train.jsonl`, `sft_validation.jsonl`,
`sft_test.jsonl` from `ml/data/llm/final/`. They contain collected page text, so upload them
to this session only; do not commit or share them.

Steps: install → configure → mount Drive → upload data → build examples → load model → train →
evaluate (zero-shot vs fine-tuned answers on the test set) → merge → export GGUF. The risk
the app would show is scored on the Mac (`guadian_ml.llm.evaluation.gguf`), where the
verdict table and the page facts are.

Everything a run produces (checkpoints, adapter, scores, GGUF) is written to
`MyDrive/guadian-ondevice/<run>` as it is made, because a free session can end at any
time and takes its disk with it. If it does, reconnect, run the cells again from the
top, and training resumes from the last saved epoch."""

INSTALL = """\
%pip install -q -U transformers peft bitsandbytes accelerate
# Colab preinstalls an old torchao, which peft rejects when merging the adapter.
%pip uninstall -q -y torchao"""

CONFIG = """\
MODEL_ID = "Qwen/Qwen3-1.7B"
RUN = "__RUN__"            # names the exported files, so runs do not overwrite each other
SEED = 20260926
# Train on the full-precision base (plain LoRA) rather than a 4-bit one (QLoRA). An adapter
# trained against 4-bit weights no longer fits once merged into the full-precision weights the
# GGUF is made from: run 1 lost 6 points of accuracy in the merge alone. 1.7B fits a T4 either way.
QUANTIZE_BASE = False
MAX_LENGTH = 1024          # longest compact prompt is ~700 tokens
LORA = dict(r=16, lora_alpha=32, lora_dropout=0.05,
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"])
# Run 3 took 275 optimizer steps (5 epochs of 381 cases at an effective batch of 8).
# Runs 4 and 5 train on 1,465 cases, so 3 epochs already give about twice as many
# steps; the best epoch by validation loss is kept (epoch 2 in run 4). Run 5 keeps
# run 4's settings and changes only the labels, so the two runs compare cleanly.
TRAIN = dict(num_train_epochs=3, learning_rate=2e-4, per_device_train_batch_size=4,
             gradient_accumulation_steps=2, lr_scheduler_type="cosine")
# Training copies per risk level. HIGH is the rarest label (78 of 1,465 in run 4,
# down from 59 of 381); three copies bring it back to about the share run 3
# trained on. Validation and test are never resampled.
OVERSAMPLE = {"HIGH": 3}
WARMUP_FRACTION = 0.05     # of all optimizer steps
MAX_NEW_TOKENS = 384       # one JSON line; a claim quotes a prompt line, rarely the long text line
RISKS = ["LOW", "MEDIUM", "HIGH"]
QUESTION_KEYS = ["sign_in", "payment", "investment", "reward_pressure", "content_farm"]"""

UPLOAD = """\
import json, os
from google.colab import files

NAMES = ["sft_train.jsonl", "sft_validation.jsonl", "sft_test.jsonl"]
missing = [n for n in NAMES if not os.path.exists(n)]
if missing:
    print("Select:", ", ".join(missing))
    files.upload()

def read_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]

data = {name.split("_")[1].split(".")[0]: read_jsonl(name) for name in NAMES}
print({split: len(rows) for split, rows in data.items()})"""

DRIVE = """\
import os
from google.colab import drive
drive.mount("/content/drive")
OUT = f"/content/drive/MyDrive/guadian-ondevice/{RUN}"
os.makedirs(OUT, exist_ok=True)
print("results go to", OUT)"""

EXAMPLES = """\
import json
from transformers import AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)

def prompt_text(messages):
    \"\"\"System and user turns plus the assistant header, exactly what the app sends at inference.\"\"\"
    return tokenizer.apply_chat_template(messages[:2], tokenize=False, add_generation_prompt=True,
                                         enable_thinking=False)

def to_features(example):
    \"\"\"Token IDs with the loss restricted to the answer; prompt tokens are labeled -100.\"\"\"
    prompt_ids = tokenizer(prompt_text(example["messages"]), add_special_tokens=False)["input_ids"]
    answer_ids = tokenizer(example["messages"][2]["content"] + "<|im_end|>\\n",
                           add_special_tokens=False)["input_ids"]
    input_ids = (prompt_ids + answer_ids)[:MAX_LENGTH]
    labels = ([-100] * len(prompt_ids) + answer_ids)[:MAX_LENGTH]
    return {"input_ids": input_ids, "attention_mask": [1] * len(input_ids), "labels": labels}

def copies(example, split):
    \"\"\"Training examples whose final risk is rare are repeated; the risk is not the target, the answers are.\"\"\"
    return OVERSAMPLE.get(example["risk"], 1) if split == "train" else 1

features = {split: [to_features(e) for e in rows for _ in range(copies(e, split))]
            for split, rows in data.items() if split != "test"}
lengths = sorted(len(f["input_ids"]) for f in features["train"])
print("train tokens: median", lengths[len(lengths) // 2], "max", lengths[-1])"""

MODEL = """\
import torch
from transformers import AutoModelForCausalLM, BitsAndBytesConfig
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training

torch.manual_seed(SEED)
COMPUTE_DTYPE = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16

if QUANTIZE_BASE:
    base = AutoModelForCausalLM.from_pretrained(
        MODEL_ID, device_map="auto", torch_dtype=COMPUTE_DTYPE,
        quantization_config=BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4",
                                               bnb_4bit_use_double_quant=True,
                                               bnb_4bit_compute_dtype=COMPUTE_DTYPE))
    base = prepare_model_for_kbit_training(base, use_gradient_checkpointing=True)
else:
    # PEFT keeps the adapter weights in float32 on a half-precision base, so mixed precision works.
    base = AutoModelForCausalLM.from_pretrained(MODEL_ID, device_map="auto", torch_dtype=COMPUTE_DTYPE)
    base.gradient_checkpointing_enable()
    base.enable_input_require_grads()
model = get_peft_model(base, LoraConfig(task_type="CAUSAL_LM", bias="none", **LORA))
model.print_trainable_parameters()"""

TRAINING = """\
from transformers import Trainer, TrainingArguments
from transformers.trainer_utils import get_last_checkpoint

def collate(batch):
    width = max(len(f["input_ids"]) for f in batch)
    pad = lambda seq, value: seq + [value] * (width - len(seq))
    return {
        "input_ids": torch.tensor([pad(f["input_ids"], tokenizer.pad_token_id) for f in batch]),
        "attention_mask": torch.tensor([pad(f["attention_mask"], 0) for f in batch]),
        "labels": torch.tensor([pad(f["labels"], -100) for f in batch]),
    }

# Warmup as a step count: newer transformers dropped `warmup_ratio`, older ones take only ints here.
import math
total_steps = math.ceil(len(features["train"]) / (TRAIN["per_device_train_batch_size"]
                                                   * TRAIN["gradient_accumulation_steps"])) * TRAIN["num_train_epochs"]
args = TrainingArguments(
    output_dir=f"{OUT}/checkpoints", seed=SEED, **TRAIN, warmup_steps=max(1, round(WARMUP_FRACTION * total_steps)),
    per_device_eval_batch_size=4, eval_strategy="epoch", save_strategy="epoch",
    load_best_model_at_end=True, metric_for_best_model="eval_loss", save_total_limit=2,
    logging_steps=10, gradient_checkpointing=True, report_to="none",
    bf16=COMPUTE_DTYPE == torch.bfloat16, fp16=COMPUTE_DTYPE == torch.float16)
trainer = Trainer(model=model, args=args, data_collator=collate,
                  train_dataset=features["train"], eval_dataset=features["validation"])
# Resumes from the last epoch saved to Drive when an earlier session ended mid-training.
last = get_last_checkpoint(args.output_dir) if os.path.isdir(args.output_dir) else None
if last:
    print("resuming from", last)
trainer.train(resume_from_checkpoint=last)
model.save_pretrained(f"{OUT}/adapter")
tokenizer.save_pretrained(f"{OUT}/adapter")"""

SCORING = """\
import json

def first_json_object(text):
    \"\"\"The first JSON object in the text, parsed properly so braces inside strings are handled.\"\"\"
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

def parse_answer(text):
    \"\"\"The answers in the reply, or None when a yes/no key is not a boolean or claims is not a string or null.\"\"\"
    answer = first_json_object(text)
    if answer is None or any(not isinstance(answer.get(k), bool) for k in QUESTION_KEYS):
        return None
    claims = answer.get("claims")
    return answer if claims is None or isinstance(claims, str) else None

def score(examples, replies):
    \"\"\"Per-question agreement with the teacher's answers, and unparseable replies (wrong on every question).

    For each question: accuracy, and recall and precision of yes. `claims` counts whether a claim
    was made, not the name.
    \"\"\"
    gold = [json.loads(e["messages"][2]["content"]) for e in examples]
    parsed = [parse_answer(r) for r in replies]
    report = {"n": len(gold), "unparseable": sum(p is None for p in parsed)}
    for key in ["claims"] + QUESTION_KEYS:
        g = [bool(a[key]) for a in gold]
        p = [bool(a[key]) if a is not None else None for a in parsed]
        hits = [x is not None and x == y for x, y in zip(p, g)]
        report[f"{key}_accuracy"] = sum(hits) / len(g)
        yes = sum(g)
        said = sum(1 for x in p if x)
        report[f"{key}_recall"] = sum(1 for x, y in zip(p, g) if x and y) / yes if yes else 0.0
        report[f"{key}_precision"] = sum(1 for x, y in zip(p, g) if x and y) / said if said else 0.0
    report["all_correct"] = sum(p is not None and all(bool(p[k]) == bool(g[k]) for k in ["claims"] + QUESTION_KEYS)
                                for p, g in zip(parsed, gold)) / len(gold)
    return report

def risk_report(gold, pred):
    \"\"\"Accuracy, macro F1, per-risk recall, and missed risk for two lists of risks.\"\"\"
    report = {"n": len(gold), "accuracy": sum(g == p for g, p in zip(gold, pred)) / len(gold)}
    f1s = []
    for risk in RISKS:
        tp = sum(g == p == risk for g, p in zip(gold, pred))
        fp = sum(p == risk != g for g, p in zip(gold, pred))
        fn = sum(g == risk != p for g, p in zip(gold, pred))
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1s.append(2 * precision * recall / (precision + recall) if precision + recall else 0.0)
        report[f"recall_{risk}"] = recall
    report["macro_f1"] = sum(f1s) / len(f1s)
    # Missed risk: a MEDIUM or HIGH page answered LOW, the error that lets a user walk in unwarned.
    report["missed_risk"] = sum(g != "LOW" and p == "LOW" for g, p in zip(gold, pred))
    return report"""

EVALUATE = """\
model.eval()
tokenizer.padding_side = "left"

@torch.no_grad()
def generate(examples, batch_size=8):
    replies = []
    for i in range(0, len(examples), batch_size):
        prompts = [prompt_text(e["messages"]) for e in examples[i:i + batch_size]]
        inputs = tokenizer(prompts, return_tensors="pt", padding=True, add_special_tokens=False).to(model.device)
        out = model.generate(**inputs, max_new_tokens=MAX_NEW_TOKENS, do_sample=False,
                             pad_token_id=tokenizer.pad_token_id)
        replies += tokenizer.batch_decode(out[:, inputs["input_ids"].shape[1]:], skip_special_tokens=True)
    return replies

test = data["test"]
with model.disable_adapter():
    zero_shot = generate(test)
fine_tuned = generate(test)
results = {"zero_shot": score(test, zero_shot), "fine_tuned": score(test, fine_tuned)}
for name, report in results.items():
    print(name, {k: round(v, 3) if isinstance(v, float) else v for k, v in report.items()})

with open(f"{OUT}/test_predictions.jsonl", "w", encoding="utf-8") as f:
    for example, zs, ft in zip(test, zero_shot, fine_tuned):
        f.write(json.dumps({"case_id": example["case_id"], "label_source": example["label_source"],
                            "gold": example["messages"][2]["content"], "zero_shot": zs, "fine_tuned": ft},
                           ensure_ascii=False) + "\\n")
with open(f"{OUT}/test_metrics.json", "w") as f:
    json.dump(results, f, indent=2)"""

MERGE = """\
# Merge the adapter into full-precision weights; GGUF conversion needs a plain model.
# Runs on its own after a session restart too: it only needs the adapter saved to Drive.
import os, torch
from transformers import AutoModelForCausalLM
for name in ("model", "base", "trainer"):
    globals().pop(name, None)
torch.cuda.empty_cache()
MODEL_ID = "Qwen/Qwen3-1.7B"
RUN = "__RUN__"
from google.colab import drive
drive.mount("/content/drive")
OUT = f"/content/drive/MyDrive/guadian-ondevice/{RUN}"
from peft import PeftModel
full = AutoModelForCausalLM.from_pretrained(MODEL_ID, torch_dtype=torch.float16, device_map="auto")
merged = PeftModel.from_pretrained(full, f"{OUT}/adapter").merge_and_unload()
merged.save_pretrained("merged", safe_serialization=True)

# Training does not change the tokenizer. Copy the base model's files rather than
# re-saving them: the GGUF converter pins an older transformers that cannot read
# the tokenizer_config.json a newer one writes.
import shutil
from huggingface_hub import hf_hub_download
for name in ["tokenizer.json", "tokenizer_config.json", "vocab.json", "merges.txt"]:
    shutil.copy(hf_hub_download(MODEL_ID, name), f"merged/{name}")"""

EXPORT = """\
import os
if not os.path.exists("llama.cpp"):
    !git clone --depth 1 https://github.com/ggml-org/llama.cpp
%pip install -q -r llama.cpp/requirements/requirements-convert_hf_to_gguf.txt
# Written straight to Drive: the f16 GGUF is ~3.4 GB, too large for a browser download.
!python llama.cpp/convert_hf_to_gguf.py merged --outtype f16 --outfile "{OUT}/guadian-qwen3-1.7b-{RUN}-f16.gguf"
print("saved to", OUT)"""

OUTRO = """\
## After training, on the Mac

```bash
for q in Q8_0 Q5_K_M Q4_K_M; do
  llama-quantize guadian-qwen3-1.7b-__RUN__-f16.gguf guadian-qwen3-1.7b-__RUN__-$q.gguf $q
done
for f in f16 Q8_0 Q5_K_M Q4_K_M; do
  python -m guadian_ml.llm.evaluation.gguf guadian-qwen3-1.7b-__RUN__-$f.gguf \\
    --name __RUN__-$(echo $f | tr A-Z a-z) --gpu-layers 99
done
python -m guadian_ml.llm.evaluation.agreement __RUN__
```

The evaluation confirms quantization kept the scores (Q4_K_M did not for run 2; see
`ml/reports/llm/evaluation-report.md`).
`test_predictions.jsonl` holds page text; keep it in `ml/data/llm/eval/`."""


def main() -> None:
    nb = nbformat.v4.new_notebook()
    nb.metadata = {"accelerator": "GPU", "colab": {"gpuType": "T4"},
                   "kernelspec": {"name": "python3", "display_name": "Python 3"}}
    cells = [("md", INTRO), ("code", INSTALL), ("code", CONFIG), ("code", DRIVE), ("code", UPLOAD),
             ("code", EXAMPLES), ("code", MODEL), ("code", TRAINING), ("code", SCORING), ("code", EVALUATE),
             ("code", MERGE), ("code", EXPORT), ("md", OUTRO)]
    nb.cells = [nbformat.v4.new_markdown_cell(src) if kind == "md" else nbformat.v4.new_code_cell(src)
                for kind, src in ((kind, src.replace("__RUN__", RUN)) for kind, src in cells)]
    for i, cell in enumerate(nb.cells):   # fixed ids keep the generated file reproducible
        cell.id = f"cell-{i:02d}"
    nbformat.write(nb, OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
