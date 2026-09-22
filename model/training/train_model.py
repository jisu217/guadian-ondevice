from __future__ import annotations

import csv
import json
import math
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

from train_baseline import accuracy as baseline_accuracy
from train_baseline import evaluate as evaluate_baseline
from url_features import FEATURE_NAMES, feature_vector


LABELS = ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]
DEFAULT_DATASET = Path(__file__).parents[1] / "data" / "sample_urls.csv"
DEFAULT_ARTIFACT = Path(__file__).parents[1] / "artifacts" / "url_risk_centroid_model.json"
DEFAULT_REPORT = Path(__file__).parents[1] / "evaluation" / "trained-model-report.md"


@dataclass(frozen=True)
class ModelPrediction:
    url: str
    actual: str
    predicted: str
    distance: float


def load_rows(path: Path = DEFAULT_DATASET) -> list[dict[str, str]]:
    with path.open(newline="") as handle:
        return list(csv.DictReader(handle))


def stratified_split(rows: list[dict[str, str]], test_every: int = 5) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    buckets: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        buckets[row["label"]].append(row)

    train_rows = []
    test_rows = []
    for label in LABELS:
        for index, row in enumerate(buckets[label]):
            if index % test_every == test_every - 1:
                test_rows.append(row)
            else:
                train_rows.append(row)

    return train_rows, test_rows


def train_centroid_model(rows: list[dict[str, str]]) -> dict:
    vectors = [feature_vector(row["url"]) for row in rows]
    mins, maxes = _feature_ranges(vectors)
    grouped: dict[str, list[list[float]]] = defaultdict(list)

    for row, vector in zip(rows, vectors):
        grouped[row["label"]].append(_scale(vector, mins, maxes))

    centroids = {
        label: _mean(vectors_for_label)
        for label, vectors_for_label in grouped.items()
        if vectors_for_label
    }

    return {
        "type": "nearest-centroid",
        "labels": LABELS,
        "feature_names": FEATURE_NAMES,
        "feature_min": mins,
        "feature_max": maxes,
        "centroids": centroids,
    }


def predict(model: dict, url: str) -> tuple[str, float]:
    vector = feature_vector(url)
    scaled = _scale(vector, model["feature_min"], model["feature_max"])
    distances = {
        label: _euclidean_distance(scaled, centroid)
        for label, centroid in model["centroids"].items()
    }
    label, distance = min(distances.items(), key=lambda item: (item[1], item[0]))
    return label, distance


def evaluate_model(model: dict, rows: list[dict[str, str]]) -> list[ModelPrediction]:
    predictions = []
    for row in rows:
        predicted, distance = predict(model, row["url"])
        predictions.append(
            ModelPrediction(
                url=row["url"],
                actual=row["label"],
                predicted=predicted,
                distance=distance,
            )
        )
    return predictions


def accuracy(predictions: list[ModelPrediction]) -> float:
    if not predictions:
        return 0.0
    correct = sum(1 for prediction in predictions if prediction.actual == prediction.predicted)
    return correct / len(predictions)


def render_report(
    train_rows: list[dict[str, str]],
    test_rows: list[dict[str, str]],
    predictions: list[ModelPrediction],
    baseline_predictions,
) -> str:
    actual_counts = Counter(prediction.actual for prediction in predictions)
    predicted_counts = Counter(prediction.predicted for prediction in predictions)
    model_acc = accuracy(predictions)
    baseline_acc = baseline_accuracy(baseline_predictions)

    lines = [
        "# Trained Model Evaluation",
        "",
        "Model: nearest-centroid classifier",
        f"Train samples: {len(train_rows)}",
        f"Test samples: {len(test_rows)}",
        f"Model accuracy: {model_acc:.2%}",
        f"Baseline accuracy on same test split: {baseline_acc:.2%}",
        "",
        "## Label Distribution",
        "",
        "| Label | Actual | Predicted |",
        "| :-- | --: | --: |",
    ]

    for label in LABELS:
        lines.append(f"| {label} | {actual_counts[label]} | {predicted_counts[label]} |")

    lines.extend(
        [
            "",
            "## Predictions",
            "",
            "| Actual | Predicted | Distance | URL |",
            "| :-- | :-- | --: | :-- |",
        ]
    )

    for prediction in predictions:
        lines.append(
            f"| {prediction.actual} | {prediction.predicted} | {prediction.distance:.4f} | {prediction.url} |"
        )

    return "\n".join(lines) + "\n"


def save_model(model: dict, path: Path = DEFAULT_ARTIFACT) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model, indent=2) + "\n")


def save_report(report: str, path: Path = DEFAULT_REPORT) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(report)


def _feature_ranges(vectors: list[list[float]]) -> tuple[list[float], list[float]]:
    transposed = list(zip(*vectors))
    return [min(values) for values in transposed], [max(values) for values in transposed]


def _scale(vector: list[float], mins: list[float], maxes: list[float]) -> list[float]:
    scaled = []
    for value, minimum, maximum in zip(vector, mins, maxes):
        if maximum == minimum:
            scaled.append(0.0)
        else:
            scaled.append((value - minimum) / (maximum - minimum))
    return scaled


def _mean(vectors: list[list[float]]) -> list[float]:
    return [sum(values) / len(values) for values in zip(*vectors)]


def _euclidean_distance(left: list[float], right: list[float]) -> float:
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(left, right)))


def main() -> None:
    rows = load_rows()
    train_rows, test_rows = stratified_split(rows)
    model = train_centroid_model(train_rows)
    predictions = evaluate_model(model, test_rows)
    baseline_predictions = evaluate_baseline(test_rows)
    report = render_report(train_rows, test_rows, predictions, baseline_predictions)

    save_model(model)
    save_report(report)
    print(report)


if __name__ == "__main__":
    main()
