# GUADIAN On-device

![Status](https://img.shields.io/badge/status-in_progress-111111)

> 「GUADIAN」은 SKT FLY AI 부트캠프에서 개발한 악성 광고에서 시작되는 디지털 피해를 예방하는 서비스이다. 본 저장소는 「GUADIAN」의 “탐지 → 판정 → 개입” 흐름 중 판정 단계에 집중하여, 기존의 판정 구조를 온디바이스로 전환한다.

The on-device judgment pipeline is as follows. Each stage returns its verdict
when it is sufficiently certain; otherwise, it passes the request to the next
stage. ① **Rule Engine** performs clear rule-based judgment, including a
bundled phishing and malware blocklist. ② **Local Verdict Store** reuses
previous judgments for the same URL; it runs after the rules so a cached
verdict never skips them. ③ **Local RAG** is planned as a local evidence
retrieval layer; today it matches local policy snippets by keyword. ④ **Page
Content Inspection** loads the page in a hidden WebView and reads its forms,
text, and links. ⑤ **On-device LLM**, a fine-tuned Qwen3-1.7B run with
llama.cpp, reads what the page showed and gives the final verdict. ⑥
**Verdict** is the first certain result.

- [history.md](reference/original-guadian/docs/history.md) — original 「GUADIAN」 project history
- [Technical Report](docs/technical-report.md) — design, evaluation, and the URL model that was tried and removed
- [LLM Model Card](ml/docs/llm/model-card.md) — the on-device model's use, data, results, and limits

## 1. Current Results

The on-device LLM (run 2, Q5_K_M GGUF) on the 118-case test split, grouped by
site so no test site was seen in training:

| Metric | Value |
| :-- | --: |
| Accuracy | 66.9% |
| Macro F1 | 0.67 |
| `HIGH` recall | 52.2% |
| Missed risk (`MEDIUM` or `HIGH` answered `LOW`) | 20 of 70 |
| Untuned base accuracy | 43.2% |

Fine-tuning taught the model the answer format and lifted accuracy by 23.7
points, but about one risky page in three is still answered `LOW`; that is the
next thing to fix. Source:
[LLM Evaluation Report](ml/reports/llm/evaluation-report.md).

Before the LLM, the rules and local knowledge settle part of the traffic
cheaply. On the 668-row URL dataset:

| Resolved by | Resolved | Correct |
| :-- | --: | --: |
| Deterministic rules | 135 | 98 |
| Local knowledge | 86 | 55 |
| **Total local** | **221** | **153** |

The other 447 rows go on to page inspection and the LLM. Source:
[Local Resolution Report](ml/reports/pipeline/local-resolution-report.md).
These are evaluation-set numbers, not production estimates.

## 2. How It Works

```text
Ad / link destination candidate
  ↓
Rule Engine
  ↓
Local Verdict Store
  ↓
Local RAG / local evidence   | RAG planned
  ↓
Page Content Inspection
  ↓
On-device LLM
  ↓
Verdict
```

This flow corresponds to the judgment stage of 「GUADIAN」's
detect-judge-intervene structure. Its input is a destination candidate, provided
in the current implementation by manual URL entry (`JudgeActivity`) or a
supported browser link click (`ShieldAccessibilityService`). Browser clicks
skip page inspection and the LLM, because the browser is already loading the
page. See [Architecture](docs/architecture.md).

Each stage name above maps to one runtime class; file paths are under
`<module>/src/main/kotlin/com/guadian/ondevice/`:

| Stage | Class | Module | File |
| :-- | :-- | :-- | :-- |
| Rule Engine | `RuleBasedJudge` (`BlocklistRule`, `ApkRule`, `BrandImpersonationRule`, `SuspiciousUrlRule`) | `core/judge` | `rules/` |
| Local Verdict Store | `InMemoryVerdictStore` (run as `StoreJudge`) | `core/judge` | `store/VerdictStore.kt` |
| Local RAG / local evidence | `LocalKnowledgeJudge` (keyword retrieval today) | `core/judge` | `knowledge/LocalKnowledgeJudge.kt` |
| Page Content Inspection | `PageContentJudge` | `core/pagecontent` | `pagecontent/PageContentJudge.kt` |
| On-device LLM | `LlmJudge`, run by `LlamaCppRuntime` | `core/llm`, `core/llamacpp` | `llm/LlmJudge.kt` |
| Verdict | `Verdict`, assembled by `HybridJudge` | `core/judge` | `judge/` |

## 3. Current Scope

The pipeline runs on an arm64 emulator. The model file is not yet delivered
by the app, and physical-device validation, latency on real phones, and
broader Shield coverage remain future work.
