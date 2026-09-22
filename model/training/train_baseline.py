from __future__ import annotations

import csv
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from url_features import extract_features


LABELS = ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]
DEFAULT_DATASET = Path(__file__).parents[1] / "data" / "sample_urls.csv"


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
        f"Samples: {len(predictions)}",
        f"Accuracy: {accuracy(predictions):.2%}",
        "",
        "## Label Distribution",
        "",
        "| Label | Actual | Predicted |",
        "| :-- | --: | --: |",
    ]

    for label in LABELS:
        lines.append(f"| {label} | {counts['actual'][label]} | {counts['predicted'][label]} |")

    lines.extend(
        [
            "",
            "## Predictions",
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


def main() -> None:
    rows = load_rows()
    print(render_report(evaluate(rows)))


if __name__ == "__main__":
    main()
