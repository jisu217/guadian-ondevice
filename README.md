# GUADIAN On-device

![Status](https://img.shields.io/badge/status-in_progress-111111)

「GUADIAN」 is a service developed in the SKT FLY AI bootcamp to prevent digital
harm originating from malicious ads. This repository focuses on the judgment
stage of the detect → judge → intervene flow and rebuilds the original
server-based judgment architecture as an on-device pipeline.

This repository records
that process: building the data, fine-tuning Qwen3-1.7B, comparing training
runs and quantized models, and connecting the model to the app.

## 1. Model Development

### Data and Judgment Task

The original service used `claude-haiku-4-5` as its server judge. For the local
model, I built a dataset from the team's 251 ad landing URLs and 300 OpenPhish
URLs. Pages were rendered in a mobile browser, labeled using the original
judge's criteria, and corrected through labeling rules and human review.

The 551 cases were split into 381 training, 52 validation, and 118 test cases,
grouped by site so that test sites do not appear in training. Labels are mostly
teacher-generated; all 47 hand-reviewed cases are in the test split.

For run 4, I grew the dataset to 2,094 cases with OpenPhish, Tranco, and Naver
search URLs, aimed at the page types the model missed, such as consultation
forms. The earlier split was kept, giving 1,465 training, 209 validation, and
420 test cases, and every test case was reviewed against the criteria.

Qwen3-1.7B reads a compact Korean prompt containing the URL, page text, form
fields, links, and other page observations. It returns `risk`, `type`, and
supporting evidence for a `HIGH` judgment in JSON. The task includes learning
both the risk criteria and the response format the app can read.

### Training Experiments

| Run | What I tried | What I found |
| :-- | :-- | :-- |
| 1 | QLoRA on a 4-bit base, then merging into fp16 for GGUF export | Accuracy fell by about six percentage points after merging, so I changed the later runs to LoRA on the fp16 base used for export. |
| 2 | LoRA on fp16 for 3 epochs, with an effective batch size of 16 | Responses followed the format, but quantization changed many answers: Q4_K_M agreed with f16 on only 65.3% of test cases. |
| 3 | 5 epochs, an effective batch size of 8, and repeating `HIGH` training cases twice | Q4_K_M agreement with f16 rose to 91.5%, and accuracy reached 74.6%. Risky pages classified as `LOW` remained a problem. |
| 4 | The 2026-10 dataset, 3 epochs, and repeating `HIGH` training cases three times | Missed risk fell sharply, but the model warned on many ordinary pages; on the human-reviewed cases it scored below run 3. |
| 5 | Run 4's settings with training labels aligned to the reviewed test labels | False alarms fell, but missed risk rose again and the model tied brand mentions to `personal_info`, a link the label rule's exceptions created. |

Run 1 used an earlier dataset version. Eight test prompts also changed between
runs 2 and 3, so these experiments do not isolate the effect of training
settings alone. Run 4 changed the data, the prompt, and the settings together.

### Evaluation and Model Selection

I compared f16, Q8_0, Q5_K_M, and Q4_K_M exports. Run 3's Q4_K_M was the
smallest file at 1.11 GB and had the highest accuracy on this test split, so
I selected it as the current model. The small test set does not establish
that 4-bit quantization is generally more accurate.

| Model | Accuracy | Macro F1 | `HIGH` recall | Unparseable replies |
| :-- | --: | --: | --: | --: |
| Untuned Qwen3-1.7B, fp16 | 43.2% | 0.25 | 0.0% | 112 / 118 |
| Run 2, Q5_K_M | 66.9% | 0.67 | 52.2% | 0 / 118 |
| Run 3, Q4_K_M | 74.6% | 0.73 | 56.5% | 0 / 118 |

The untuned model usually failed to follow the response format, so its score
reflects that failure as well as risk judgment. The base was evaluated in
Colab; GGUF files were evaluated on a Mac, not on a phone.

The main lesson was that better overall accuracy did not necessarily reduce
missed risk. Run 3 Q4_K_M classified 21 of 70 risky pages as `LOW`, compared
with 20 for run 2 Q5_K_M. Nine of run 3's misses were pages collecting personal
details. On the 47 hand-reviewed cases, accuracy was 68.1%, with no `HIGH`
cases correctly identified.

On the 420-case test split, run 4 missed 20 of 208 risky pages against 65 for
run 3, but it warned on more ordinary pages and scored lower on the
human-reviewed cases (67.7% against 71.4%). The extra warnings came from
training labels that called consultation pages `personal_info` even without a
personal field, which the reviewed test labels do not. Run 5 aligned those
labels but missed more risky pages (27 against 20) and fell below run 3 on the
human-reviewed cases as the app reads them, so neither run replaced run 3 in
the app. Scored the same way, the original server judge misses the fewest risky
pages but warns on most ordinary ones, because the reviewed labels now follow
stricter criteria than the original.

So I rewrote the criteria as one labeling guide, built on the original
service's rule that a page is blocked only when the risk is certain, serious,
and on a site that is not the brand's own. The teacher relabeled every case
with it, reading the same prompt as the on-device model, and now agrees with
the human labels on 83.5% of reviewed cases. I left out 38 pages that a bot
defense served to the collector, settled the remaining disagreements case by
case, and rescored earlier runs on the relabeled test split before training
run 6. Because I wrote both the guide and the reviewed labels, I also score what
needs no label: how often a model passes a phishing URL that OpenPhish confirmed,
and how often it warns on a top site.

The real cost was that the criteria lived in the model, so each change of rules
meant relabeling and training again, and five parts of the app each decided by
their own rules. Before training again I am redesigning the verdict so that the
model answers a few questions and one table decides the risk
([Verdict Design](docs/design.md)).

Details: [Dataset](ml/docs/llm/dataset.md),
[Model Card](ml/docs/llm/model-card.md), and
[Evaluation Report](ml/reports/llm/evaluation-report.md).

## 2. On-device Integration

After training, I merged the adapter, exported GGUF files, and connected
llama.cpp to the Kotlin Android app. The app's prompt construction, chat
template, and response parsing are checked against Python fixtures so that
training and runtime use the same contract.

The judgment pipeline, as the app now runs it:

```text
URL
  -> Cache & URL
  -> Page
  -> On-device LLM
  -> Verdict (answer check -> verdict table)
```

When the cache-and-URL stage or the page stage settles the result, the pipeline
exits early, so the costly model inference runs only when the earlier stages
reach no conclusion.

Only the verdict table ([Verdict Design](docs/design.md)) sets a risk; the
stages before it collect facts. Page inspection uses a hidden WebView, and the
app and the training-data collector read pages with the same script, which
names form fields (email, name, phone, search) and leaves out fields the user
cannot see. The app reads both kinds of model reply. A question model's answers
go to the verdict table, and a claim counts only when its evidence is one of the
prompt's lines. The shipped run 3 model still grades the risk itself, so its reply
is read as before: the app checks the evidence returned with a `HIGH` answer and
lowers the risk to `MEDIUM` when the required grounding is missing, and lowers a
`personal_info` answer to `LOW` unless the page shows a personal field or a form
worded to collect contact details. The first question model, run 7, is labeled
and ready to train.

| Area | Verified so far | Still to do |
| :-- | :-- | :-- |
| Android runtime | Model loading and verdict generation on an arm64 emulator | Physical-device validation |
| Inference time | About 4–5 seconds per verdict with a 4-bit run 1 model on the emulator | Timing the selected run 3 model and measuring latency and memory on a real phone |
| Model setup | Manual copying into the app's private storage for development | The model is not bundled or distributed; data terms constrain distribution |
| Browser integration | Shield implements browser-click judgment and Logcat output, running the cache, URL, and page stages but not the LLM | Manual verification of Shield logs, its 3-second budget against page loading, and broader surface coverage |

This reaches model development and Android emulator integration, with real
device validation still pending. Runtime structure and model-loading commands
are in [Architecture](docs/architecture.md).
