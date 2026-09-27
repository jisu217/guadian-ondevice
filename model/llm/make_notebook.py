"""Generate train_qlora.ipynb, the Colab notebook that fine-tunes the on-device judge.

The notebook is generated rather than hand-edited so its code can be reviewed as
plain Python and the pure-Python parts (example building, scoring) can be tested
locally without a GPU; see test_train_qlora.py.

Usage:
    python model/llm/make_notebook.py
"""

from __future__ import annotations

import os

import nbformat

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "train_qlora.ipynb")

INTRO = """\
# QLoRA fine-tuning of the on-device judge

Fine-tunes `Qwen/Qwen3-1.7B` on the compact examples from `model/llm/build_prompts.py`
and exports the merged model as an f16 GGUF. Quantize it to 4-bit on the Mac afterwards.

**Runtime:** GPU (T4 is enough). **Inputs:** `sft_train.jsonl`, `sft_validation.jsonl`,
`sft_test.jsonl` from `model/llm/data/`. They contain collected page text, so upload them
to this session only; do not commit or share them.

Steps: install → configure → upload data → build examples → load model → train →
evaluate (zero-shot vs fine-tuned on the test set) → merge → export GGUF."""

INSTALL = """\
%pip install -q -U transformers peft bitsandbytes accelerate"""

CONFIG = """\
MODEL_ID = "Qwen/Qwen3-1.7B"
SEED = 20260926
MAX_LENGTH = 1024          # longest compact prompt is ~700 tokens
LORA = dict(r=16, lora_alpha=32, lora_dropout=0.05,
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"])
TRAIN = dict(num_train_epochs=3, learning_rate=2e-4, per_device_train_batch_size=4,
             gradient_accumulation_steps=4, warmup_ratio=0.05, lr_scheduler_type="cosine")
MAX_NEW_TOKENS = 96        # the answer is one short JSON line
RISKS = ["LOW", "MEDIUM", "HIGH"]"""

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

EXAMPLES = """\
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

features = {split: [to_features(e) for e in rows] for split, rows in data.items() if split != "test"}
lengths = sorted(len(f["input_ids"]) for f in features["train"])
print("train tokens: median", lengths[len(lengths) // 2], "max", lengths[-1])"""

MODEL = """\
import torch
from transformers import AutoModelForCausalLM, BitsAndBytesConfig
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training

torch.manual_seed(SEED)
COMPUTE_DTYPE = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16

base = AutoModelForCausalLM.from_pretrained(
    MODEL_ID, device_map="auto", torch_dtype=COMPUTE_DTYPE,
    quantization_config=BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4",
                                           bnb_4bit_use_double_quant=True,
                                           bnb_4bit_compute_dtype=COMPUTE_DTYPE))
base = prepare_model_for_kbit_training(base, use_gradient_checkpointing=True)
model = get_peft_model(base, LoraConfig(task_type="CAUSAL_LM", bias="none", **LORA))
model.print_trainable_parameters()"""

TRAINING = """\
from transformers import Trainer, TrainingArguments

def collate(batch):
    width = max(len(f["input_ids"]) for f in batch)
    pad = lambda seq, value: seq + [value] * (width - len(seq))
    return {
        "input_ids": torch.tensor([pad(f["input_ids"], tokenizer.pad_token_id) for f in batch]),
        "attention_mask": torch.tensor([pad(f["attention_mask"], 0) for f in batch]),
        "labels": torch.tensor([pad(f["labels"], -100) for f in batch]),
    }

args = TrainingArguments(
    output_dir="checkpoints", seed=SEED, **TRAIN,
    per_device_eval_batch_size=4, eval_strategy="epoch", save_strategy="epoch",
    load_best_model_at_end=True, metric_for_best_model="eval_loss", save_total_limit=2,
    logging_steps=10, gradient_checkpointing=True, report_to="none",
    bf16=COMPUTE_DTYPE == torch.bfloat16, fp16=COMPUTE_DTYPE == torch.float16)
trainer = Trainer(model=model, args=args, data_collator=collate,
                  train_dataset=features["train"], eval_dataset=features["validation"])
trainer.train()
model.save_pretrained("adapter")
tokenizer.save_pretrained("adapter")"""

SCORING = """\
import json, re

def parse_answer(text):
    \"\"\"The first JSON object in the reply, or None if there is none or it lacks a valid risk.\"\"\"
    match = re.search(r"\\{.*?\\}", text, re.S)
    if not match:
        return None
    try:
        answer = json.loads(match.group(0))
    except json.JSONDecodeError:
        return None
    return answer if answer.get("risk") in RISKS else None

def score(examples, replies):
    \"\"\"Accuracy, macro F1, per-risk recall, missed risk, and unparseable replies.

    A reply that cannot be parsed counts as MEDIUM, the app's fallback for malformed output.
    \"\"\"
    gold = [json.loads(e["messages"][2]["content"])["risk"] for e in examples]
    parsed = [parse_answer(r) for r in replies]
    pred = [p["risk"] if p else "MEDIUM" for p in parsed]
    report = {"n": len(gold), "accuracy": sum(g == p for g, p in zip(gold, pred)) / len(gold),
              "unparseable": sum(p is None for p in parsed)}
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

with open("test_predictions.jsonl", "w", encoding="utf-8") as f:
    for example, zs, ft in zip(test, zero_shot, fine_tuned):
        f.write(json.dumps({"case_id": example["case_id"], "label_source": example["label_source"],
                            "gold": example["messages"][2]["content"], "zero_shot": zs, "fine_tuned": ft},
                           ensure_ascii=False) + "\\n")
with open("test_metrics.json", "w") as f:
    json.dump(results, f, indent=2)"""

MERGE = """\
# Merge the adapter into full-precision weights; GGUF conversion needs a plain model.
del model, base, trainer
torch.cuda.empty_cache()
from peft import PeftModel
full = AutoModelForCausalLM.from_pretrained(MODEL_ID, torch_dtype=torch.float16, device_map="auto")
merged = PeftModel.from_pretrained(full, "adapter").merge_and_unload()
merged.save_pretrained("merged", safe_serialization=True)
tokenizer.save_pretrained("merged")"""

EXPORT = """\
!git clone --depth 1 https://github.com/ggml-org/llama.cpp
%pip install -q -r llama.cpp/requirements/requirements-convert_hf_to_gguf.txt
!python llama.cpp/convert_hf_to_gguf.py merged --outtype f16 --outfile guadian-qwen3-1.7b-f16.gguf"""

SAVE = """\
# Copy results to Google Drive (the f16 GGUF is ~3.4 GB, too large for a browser download).
from google.colab import drive
import shutil
drive.mount("/content/drive")
target = "/content/drive/MyDrive/guadian-ondevice"
os.makedirs(target, exist_ok=True)
for path in ["guadian-qwen3-1.7b-f16.gguf", "test_metrics.json", "test_predictions.jsonl"]:
    shutil.copy(path, target)
shutil.copytree("adapter", f"{target}/adapter", dirs_exist_ok=True)
print("saved to", target)"""

OUTRO = """\
## After training, on the Mac

```bash
llama-quantize guadian-qwen3-1.7b-f16.gguf guadian-qwen3-1.7b-Q4_K_M.gguf Q4_K_M
```

Then re-run the test set against the 4-bit file to confirm quantization kept the scores.
`test_predictions.jsonl` holds page text; keep it in `model/llm/data/`."""


def main() -> None:
    nb = nbformat.v4.new_notebook()
    nb.metadata = {"accelerator": "GPU", "colab": {"gpuType": "T4"},
                   "kernelspec": {"name": "python3", "display_name": "Python 3"}}
    cells = [("md", INTRO), ("code", INSTALL), ("code", CONFIG), ("code", UPLOAD), ("code", EXAMPLES),
             ("code", MODEL), ("code", TRAINING), ("code", SCORING), ("code", EVALUATE),
             ("code", MERGE), ("code", EXPORT), ("code", SAVE), ("md", OUTRO)]
    nb.cells = [nbformat.v4.new_markdown_cell(src) if kind == "md" else nbformat.v4.new_code_cell(src)
                for kind, src in cells]
    nbformat.write(nb, OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
