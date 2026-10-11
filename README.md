# GUADIAN On-device

![Status](https://img.shields.io/badge/status-in_progress-111111)

「GUADIAN」 is a service my team built in the SKT FLY AI bootcamp to protect people
from malicious ads; its judgment stage called `claude-haiku-4-5` on a server. In
this repository I moved that judgment onto the phone, and most of the work went
into one question: how far can a 1.7B model, fine-tuned and quantized to run on
a phone's CPU, be pushed toward a reliable judge? This is the record of that
work: the data, six training runs, and what each one taught me.

## The task

The model reads a short Korean description of an ad landing page, built from
what the phone can observe (address, title, visible text, form fields, APK
links, host-imitation findings), and answers in one JSON line that the app reads.
I fine-tune Qwen3-1.7B with LoRA, export it to GGUF, and run it with llama.cpp
inside an Android app.

I started from the team's 251 ad landing URLs and 300 OpenPhish URLs, rendered
in a mobile browser and labeled by the original judge's criteria, then grew the
data to 2,094 cases with OpenPhish, Tranco Korean sites, and Naver search
results. Splits are grouped by site, so no test site appears in training.

## Training runs

| Run | What I changed | Result |
| :-- | :-- | :-- |
| 1 | QLoRA on a 4-bit base, merged into fp16 for export | Accuracy fell about six points in the merge |
| 2 | LoRA on the fp16 base | Format followed, but Q4_K_M agreed with f16 on only 65.3% of cases |
| 3 | 5 epochs, effective batch 8, `HIGH` cases repeated | Agreement 91.5%, accuracy 74.6%; one risky page in three answered `LOW` |
| 4 | 2,094-case dataset, `HIGH` repeated three times | Far fewer misses, but many false alarms |
| 5 | Run 4 with training labels aligned to the reviewed test labels | Fewer false alarms, more misses again |
| 6 | Six questions answered by the model, risk set by a verdict table | 78.8% on human-reviewed cases (run 3: 72.4%), 24 of 188 risky pages missed |

Run 1 taught me that an adapter trained against 4-bit weights no longer fits
once merged into the full-precision weights the GGUF is made from, so every
later run trains on the fp16 base. Run 2 showed the opposite problem at the
other end: the fine-tuned model was fragile under quantization, and more
optimizer steps in run 3 made its 4-bit file agree with f16 on 91.5% of cases
instead of 65.3%.

Runs 4 and 5 were the turning point. Run 4 cut missed risk from 65 to 20 on the
new test split but warned on many ordinary pages, because its training labels
called consultation pages `personal_info` even without a personal field. When I
aligned those labels in run 5, missed risk rose again, and the model learned to
tie brand mentions to `personal_info` through an exception in the label rule.
Between runs 4 and 5 overall accuracy barely moved (84.0% and 84.5%) while
missed risk and false alarms moved in opposite directions, and I only saw it
because I measured them separately.

The deeper problem was that the risk criteria lived inside the model. Every
change to the rules meant relabeling and retraining, and the app's own checks
drifted from the labels. So for run 6 I changed what the model is asked: it
answers six factual questions about the page (does it claim to be a brand, is it
a sign-in screen, does it ask for payment, and so on), and one verdict table,
shared by the app and the training code, turns those answers and facts code
computes into the risk. Changing a rule now means editing the table, not
training again.

## Where it stands

Run 6 ships as Q8_0 (1.83 GB). As the app reads it, it scores 92.0% on the
414-case test split and 78.8% on the 156 human-reviewed cases, misses 24 of 188
risky pages, and warns on 2 of 62 top sites that opened. I picked Q8_0 over
Q4_K_M because the 4-bit file loses impersonation claims (claims recall 68.9%
against 80.0%); on an arm64 emulator a Q8_0 verdict took 1.03–1.18 times as long.
A Mac benchmark had first shown Q8_0 twice as fast, most likely because the
Homebrew build runs long prompts through BLAS, which the phone build does not
use.

Two things qualify these numbers. Run 6 first missed 34 risky pages, over my
bar of 25; I relabeled the 23 cases where it disagreed with a Claude-written
label, blind to the old label, and changed 11. That review covered only run 6's
disagreements, so it favors run 6 against earlier runs, and every human label
is mine. And 11 of 43 phishing pages that opened are still answered `LOW`; no
timing or memory has been measured on a physical phone yet.

## More

- [Technical Report](docs/technical-report.md): the full development record
- [LLM Evaluation Report](ml/reports/llm/evaluation-report.md): every score, generated from committed records
- [Model Card](ml/docs/llm/model-card.md) and [LLM Dataset](ml/docs/llm/dataset.md)
- [Verdict Design](docs/design.md) and [Architecture](docs/architecture.md): the verdict table and the Android runtime
