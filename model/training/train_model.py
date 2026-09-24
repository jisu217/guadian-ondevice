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
DEFAULT_FOLDS = 5
DEFAULT_CERTAIN_DISTANCE_THRESHOLD = 1.2


@dataclass(frozen=True)
class ModelPrediction:
    url: str
    actual: str
    predicted: str
    distance: float


@dataclass(frozen=True)
class FoldEvaluation:
    fold: int
    train_samples: int
    validation_samples: int
    accuracy: float
    macro_f1: float


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


def stratified_kfold_split(rows: list[dict[str, str]], folds: int = DEFAULT_FOLDS) -> list[tuple[list[dict[str, str]], list[dict[str, str]]]]:
    buckets: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        buckets[row["label"]].append(row)

    splits = []
    for fold in range(folds):
        train_rows = []
        validation_rows = []
        for label in LABELS:
            for index, row in enumerate(buckets[label]):
                if index % folds == fold:
                    validation_rows.append(row)
                else:
                    train_rows.append(row)
        splits.append((train_rows, validation_rows))

    return splits


def train_centroid_model(
    rows: list[dict[str, str]],
    certain_distance_thresholds: dict[str, float] | None = None,
) -> dict:
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

    thresholds = certain_distance_thresholds or {
        label: DEFAULT_CERTAIN_DISTANCE_THRESHOLD for label in LABELS
    }

    return {
        "type": "nearest-centroid",
        "labels": LABELS,
        "feature_names": FEATURE_NAMES,
        "feature_min": mins,
        "feature_max": maxes,
        "centroids": centroids,
        "certain_distance_thresholds": thresholds,
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


def precision_recall_by_label(predictions) -> dict[str, dict[str, float]]:
    metrics = {}
    for label in LABELS:
        true_positive = sum(1 for prediction in predictions if prediction.actual == label and prediction.predicted == label)
        predicted_positive = sum(1 for prediction in predictions if prediction.predicted == label)
        actual_positive = sum(1 for prediction in predictions if prediction.actual == label)
        precision = _safe_divide(true_positive, predicted_positive)
        recall = _safe_divide(true_positive, actual_positive)

        metrics[label] = {
            "precision": precision,
            "recall": recall,
            "f1": _safe_divide(2 * precision * recall, precision + recall),
            "support": float(actual_positive),
        }

    return metrics


def macro_f1(predictions) -> float:
    metrics = precision_recall_by_label(predictions)
    return sum(metrics[label]["f1"] for label in LABELS) / len(LABELS)


def confusion_matrix(predictions) -> dict[str, dict[str, int]]:
    return {
        actual: {
            predicted: sum(1 for item in predictions if item.actual == actual and item.predicted == predicted)
            for predicted in LABELS
        }
        for actual in LABELS
    }


def cross_validate_model(rows: list[dict[str, str]], folds: int = DEFAULT_FOLDS) -> tuple[list[FoldEvaluation], list[ModelPrediction]]:
    fold_evaluations = []
    all_predictions = []
    for fold, (train_rows, validation_rows) in enumerate(stratified_kfold_split(rows, folds), start=1):
        model = train_centroid_model(train_rows)
        predictions = evaluate_model(model, validation_rows)
        all_predictions.extend(predictions)
        fold_evaluations.append(
            FoldEvaluation(
                fold=fold,
                train_samples=len(train_rows),
                validation_samples=len(validation_rows),
                accuracy=accuracy(predictions),
                macro_f1=macro_f1(predictions),
            )
        )

    return fold_evaluations, all_predictions


def calibrate_certain_distance_thresholds(predictions: list[ModelPrediction]) -> dict[str, float]:
    """Per-label distance threshold instead of one scalar shared by all 4 labels.

    A single global threshold means one close-but-wrong prediction for ANY
    label drags the "certain" bar down for every other label too, even ones
    that are cleanly separated (see docs/interview-brief.md's Model
    Evaluation section for the case that exposed this: one LOW/MEDIUM
    boundary error at distance 0.35 collapsed HIGH's threshold from ~1.1 to
    0.35, even though HIGH itself had no errors anywhere near that close).
    Calibrating per predicted label keeps each label's threshold tied only
    to its own separation from its own nearest confusable neighbor.
    """
    thresholds: dict[str, float] = {}
    for label in LABELS:
        correct_distances = sorted(
            prediction.distance
            for prediction in predictions
            if prediction.predicted == label and prediction.actual == label
        )
        incorrect_distances = sorted(
            prediction.distance
            for prediction in predictions
            if prediction.predicted == label and prediction.actual != label
        )

        if not correct_distances:
            thresholds[label] = DEFAULT_CERTAIN_DISTANCE_THRESHOLD
            continue

        conservative_correct = _percentile(correct_distances, 0.90)
        if incorrect_distances and incorrect_distances[0] <= conservative_correct:
            thresholds[label] = max(0.0, incorrect_distances[0] - 0.0001)
        else:
            thresholds[label] = conservative_correct

    return thresholds


def render_report(
    train_rows: list[dict[str, str]],
    test_rows: list[dict[str, str]],
    predictions: list[ModelPrediction],
    baseline_predictions,
    fold_evaluations: list[FoldEvaluation] | None = None,
    cross_validation_predictions: list[ModelPrediction] | None = None,
    certain_distance_thresholds: dict[str, float] | None = None,
) -> str:
    actual_counts = Counter(prediction.actual for prediction in predictions)
    predicted_counts = Counter(prediction.predicted for prediction in predictions)
    model_acc = accuracy(predictions)
    baseline_acc = baseline_accuracy(baseline_predictions)
    model_metrics = precision_recall_by_label(predictions)
    baseline_metrics = precision_recall_by_label(baseline_predictions)
    matrix = confusion_matrix(predictions)
    fold_evaluations = fold_evaluations or []
    cross_validation_predictions = cross_validation_predictions or []
    certain_distance_thresholds = certain_distance_thresholds or {}

    lines = [
        "# Trained Model Evaluation",
        "",
        "Model: nearest-centroid classifier",
        f"Train samples: {len(train_rows)}",
        f"Test samples: {len(test_rows)}",
        f"Model accuracy: {model_acc:.2%}",
        f"Model macro F1: {macro_f1(predictions):.2%}",
        f"Baseline accuracy on same test split: {baseline_acc:.2%}",
        "",
        "## Validation Protocol",
        "",
        "- Final test metrics use a deterministic stratified holdout split.",
        f"- Threshold calibration uses {DEFAULT_FOLDS}-fold stratified cross-validation on the training split.",
        "- Each label gets its own certain-distance threshold instead of one shared scalar,",
        "  so one close call for label A can no longer collapse label B's threshold too.",
        "- Thresholds are intentionally conservative: uncertain cases should fall through to local RAG or remote fallback.",
        "",
        "### Calibrated Certain-Distance Thresholds",
        "",
        "| Label | Threshold |",
        "| :-- | --: |",
    ]

    for label in LABELS:
        lines.append(f"| {label} | {certain_distance_thresholds.get(label, DEFAULT_CERTAIN_DISTANCE_THRESHOLD):.4f} |")

    lines.append("")

    if fold_evaluations:
        average_accuracy = sum(fold.accuracy for fold in fold_evaluations) / len(fold_evaluations)
        average_macro_f1 = sum(fold.macro_f1 for fold in fold_evaluations) / len(fold_evaluations)
        lines.extend(
            [
                "## Cross Validation",
                "",
                f"Average accuracy: {average_accuracy:.2%}",
                f"Average macro F1: {average_macro_f1:.2%}",
                "",
                "| Fold | Train | Validation | Accuracy | Macro F1 |",
                "| :-- | --: | --: | --: | --: |",
            ]
        )

        for fold in fold_evaluations:
            lines.append(
                f"| {fold.fold} | {fold.train_samples} | {fold.validation_samples} | "
                f"{fold.accuracy:.2%} | {fold.macro_f1:.2%} |"
            )

        if cross_validation_predictions:
            cv_metrics = precision_recall_by_label(cross_validation_predictions)
            lines.extend(
                [
                    "",
                    "### Cross-Validation Label Metrics",
                    "",
                    "| Label | Precision | Recall | F1 | Support |",
                    "| :-- | --: | --: | --: | --: |",
                ]
            )
            for label in LABELS:
                current = cv_metrics[label]
                lines.append(
                    f"| {label} | {current['precision']:.2%} | {current['recall']:.2%} | "
                    f"{current['f1']:.2%} | {int(current['support'])} |"
                )

        lines.append("")

    lines.extend(
        [
        "## Label Distribution",
        "",
        "| Label | Actual | Predicted |",
        "| :-- | --: | --: |",
        ]
    )

    for label in LABELS:
        lines.append(f"| {label} | {actual_counts[label]} | {predicted_counts[label]} |")

    lines.extend(
        [
            "",
            "## Precision And Recall",
            "",
            "| Label | Precision | Recall | F1 | Support | Baseline Precision | Baseline Recall |",
            "| :-- | --: | --: | --: | --: | --: | --: |",
        ]
    )

    for label in LABELS:
        current = model_metrics[label]
        baseline = baseline_metrics[label]
        lines.append(
            f"| {label} | {current['precision']:.2%} | {current['recall']:.2%} | {current['f1']:.2%} | "
            f"{int(current['support'])} | {baseline['precision']:.2%} | {baseline['recall']:.2%} |"
        )

    errors = [prediction for prediction in predictions if prediction.actual != prediction.predicted]
    if errors:
        lines.extend(
            [
                "",
                "## Error Analysis",
                "",
                "| Actual | Predicted | Distance | URL |",
                "| :-- | :-- | --: | :-- |",
            ]
        )

        for prediction in errors:
            lines.append(
                f"| {prediction.actual} | {prediction.predicted} | {prediction.distance:.4f} | {prediction.url} |"
            )

    lines.extend(
        [
            "",
            "## Confusion Matrix",
            "",
            "| Actual \\ Predicted | LOW | MEDIUM | HIGH | UNKNOWN |",
            "| :-- | --: | --: | --: | --: |",
        ]
    )

    for actual in LABELS:
        row = matrix[actual]
        lines.append(
            f"| {actual} | {row['LOW']} | {row['MEDIUM']} | {row['HIGH']} | {row['UNKNOWN']} |"
        )

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


def _safe_divide(numerator: int, denominator: int) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def _percentile(values: list[float], percentile: float) -> float:
    if not values:
        return 0.0
    if len(values) == 1:
        return values[0]

    index = (len(values) - 1) * percentile
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return values[int(index)]

    lower_value = values[lower]
    upper_value = values[upper]
    return lower_value + (upper_value - lower_value) * (index - lower)


def main() -> None:
    rows = load_rows()
    train_rows, test_rows = stratified_split(rows)
    fold_evaluations, cross_validation_predictions = cross_validate_model(train_rows)
    certain_distance_thresholds = calibrate_certain_distance_thresholds(cross_validation_predictions)
    model = train_centroid_model(train_rows, certain_distance_thresholds=certain_distance_thresholds)
    predictions = evaluate_model(model, test_rows)
    baseline_predictions = evaluate_baseline(test_rows)
    report = render_report(
        train_rows,
        test_rows,
        predictions,
        baseline_predictions,
        fold_evaluations=fold_evaluations,
        cross_validation_predictions=cross_validation_predictions,
        certain_distance_thresholds=certain_distance_thresholds,
    )

    save_model(model)
    save_report(report)
    print(report)


if __name__ == "__main__":
    main()
