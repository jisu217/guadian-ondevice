from __future__ import annotations

import csv
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from url_features import extract_features


LABELS = ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]
DEFAULT_DATASET = Path(__file__).parents[1] / "data" / "sample_urls.csv"
DEFAULT_REPORT = Path(__file__).parents[1] / "evaluation" / "baseline-report.md"


@dataclass(frozen=True)
class Prediction:
    url: str
    actual: str
    predicted: str
    score: int


def predict_label(url: str) -> tuple[str, int]:
    features = extract_features(url)

    if features["official_domain_match"]:
        return "LOW", 0

    score = 0
    score += int(features["has_apk"]) * 4
    score += int(features["has_login"]) * 2
    score += int(features["brand_keyword_present"]) * 2
    score += int(features["has_event"]) * 1
    score += int(features["has_free"]) * 1
    score += int(features["has_shortener_token"]) * 1
    score += int(features["suspicious_tld"]) * 1

    if score >= 4:
        return "HIGH", score
    if score >= 2:
        return "MEDIUM", score
    if score == 1:
        return "UNKNOWN", score
    return "UNKNOWN", score


def load_rows(path: Path = DEFAULT_DATASET) -> list[dict[str, str]]:
    with path.open(newline="") as handle:
        return list(csv.DictReader(handle))


def evaluate(rows: list[dict[str, str]]) -> list[Prediction]:
    predictions = []
    for row in rows:
        predicted, score = predict_label(row["url"])
        predictions.append(
            Prediction(
                url=row["url"],
                actual=row["label"],
                predicted=predicted,
                score=score,
            )
        )
    return predictions


def accuracy(predictions: list[Prediction]) -> float:
    if not predictions:
        return 0.0
    correct = sum(1 for prediction in predictions if prediction.actual == prediction.predicted)
    return correct / len(predictions)


def label_counts(predictions: list[Prediction]) -> dict[str, Counter]:
    return {
        "actual": Counter(prediction.actual for prediction in predictions),
        "predicted": Counter(prediction.predicted for prediction in predictions),
    }


def render_report(predictions: list[Prediction]) -> str:
    counts = label_counts(predictions)
    lines = [
        "# Baseline Evaluation",
        "",
        "This report evaluates the rule-scoring baseline in `train_baseline.py` on the full committed "
        f"dataset of {len(predictions)} rows. It reaches {accuracy(predictions):.2%} accuracy.",
        "",
        "## Table of Contents",
        "",
        "1. [Scoring Rule](#1-scoring-rule)",
        "2. [Label Distribution](#2-label-distribution)",
        "3. [Predictions](#3-predictions)",
        "",
        "## 1. Scoring Rule",
        "",
        "A host that matches the official-domain allowlist is `LOW` with score 0. Otherwise the score sums "
        "feature weights: APK 4, login 2, brand keyword 2, and event, free, shortener, and suspicious TLD "
        "1 each. A score of 4 or more is `HIGH`, 2 or 3 is `MEDIUM`, and anything lower is `UNKNOWN`.",
        "",
        "## 2. Label Distribution",
        "",
        "Actual and predicted label counts over the full dataset:",
        "",
        "| Label | Actual | Predicted |",
        "| :-- | --: | --: |",
    ]

    for label in LABELS:
        lines.append(f"| {label} | {counts['actual'][label]} | {counts['predicted'][label]} |")

    lines.extend(
        [
            "",
            "## 3. Predictions",
            "",
            f"All {len(predictions)} predictions with their scores, in dataset order:",
            "",
            "| Actual | Predicted | Score | URL |",
            "| :-- | :-- | --: | :-- |",
        ]
    )

    for prediction in predictions:
        lines.append(
            f"| {prediction.actual} | {prediction.predicted} | {prediction.score} | {prediction.url} |"
        )

    return "\n".join(lines) + "\n"


def save_report(report: str, path: Path = DEFAULT_REPORT) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(report)


def main() -> None:
    rows = load_rows()
    report = render_report(evaluate(rows))
    save_report(report)
    print(report)


if __name__ == "__main__":
    main()
