from __future__ import annotations

import csv
import hashlib
import json
import math
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

from train_baseline import accuracy as baseline_accuracy
from train_baseline import evaluate as evaluate_baseline
from url_features import FEATURE_NAMES, feature_vector


LABELS = ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]

LENGTH_FEATURE_NAMES = {"url_length", "host_length", "path_length", "query_length"}
LENGTH_FEATURE_INDICES = [FEATURE_NAMES.index(name) for name in LENGTH_FEATURE_NAMES]
DEFAULT_DATASET = Path(__file__).parents[1] / "data" / "sample_urls.csv"
DEFAULT_ARTIFACT = Path(__file__).parents[1] / "runtime" / "url_risk_centroid_model.json"
DEFAULT_REPORT = Path(__file__).parents[1] / "evaluation" / "trained-model-report.md"
# Machine-readable copy of the report's headline numbers. It is the single
# source that scripts/check_doc_metrics.py compares every hand-written doc
# table against, so a retrain cannot leave a stale number in the docs.
DEFAULT_METRICS = Path(__file__).parents[1] / "evaluation" / "metrics.json"
DEFAULT_TRAIN_SPLIT = Path(__file__).parents[1] / "data" / "train-split.csv"
DEFAULT_TEST_SPLIT = Path(__file__).parents[1] / "data" / "test-split.csv"
# Bump when features, data, or training change. Stored in the artifact and
# shown by the app, so a report can be tied to the model actually shipped.
MODEL_VERSION = "v8-prep"
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


def _compress_length_features(vector: list[float]) -> list[float]:
    """Flatten long-tail URL length features before min-max scaling."""
    return [
        math.log1p(value) if index in LENGTH_FEATURE_INDICES else value
        for index, value in enumerate(vector)
    ]


def train_centroid_model(
    rows: list[dict[str, str]],
    certain_distance_thresholds: dict[str, float] | None = None,
) -> dict:
    vectors = [_compress_length_features(feature_vector(row["url"])) for row in rows]
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
        "version": MODEL_VERSION,
        "labels": LABELS,
        "feature_names": FEATURE_NAMES,
        "feature_min": mins,
        "feature_max": maxes,
        "centroids": centroids,
        "certain_distance_thresholds": thresholds,
    }


def predict(model: dict, url: str) -> tuple[str, float]:
    vector = _compress_length_features(feature_vector(url))
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


def error_buckets(predictions: list[ModelPrediction]) -> Counter[str]:
    return Counter(
        _error_bucket(prediction)
        for prediction in predictions
        if prediction.actual != prediction.predicted
    )


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
    that are cleanly separated (see docs/technical-report.md's evaluation
    discussion for the case that exposed this: one LOW/MEDIUM
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
    sources: dict[str, str] | None = None,
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

    errors = [prediction for prediction in predictions if prediction.actual != prediction.predicted]
    thresholds_text = ", ".join(
        f"{certain_distance_thresholds.get(label, DEFAULT_CERTAIN_DISTANCE_THRESHOLD):.4f} for `{label}`"
        for label in LABELS
    )

    lines = [
        "# Trained Model Evaluation",
        "",
        f"This report evaluates the nearest-centroid classifier on a deterministic stratified holdout split of "
        f"{len(train_rows)} training and {len(test_rows)} test rows. The model reaches {model_acc:.2%} accuracy "
        f"and {macro_f1(predictions):.2%} macro F1, against {baseline_acc:.2%} for the rule baseline on the same "
        "test split.",
        "",
        "## Table of Contents",
        "",
        "1. [Validation Protocol](#1-validation-protocol)",
        "2. [Cross Validation](#2-cross-validation)",
        "3. [Precision and Recall](#3-precision-and-recall)",
        "4. [Holdout by Source](#4-holdout-by-source)",
        "5. [Error Buckets](#5-error-buckets)",
        "6. [Confusion Matrix](#6-confusion-matrix)",
        "7. [Predictions](#7-predictions)",
        "",
        "## 1. Validation Protocol",
        "",
        f"Model version `{MODEL_VERSION}`. "
        "Final test metrics use a deterministic stratified holdout split, saved as "
        "`model/data/train-split.csv` and `model/data/test-split.csv`. "
        "`url_length`, `host_length`, `path_length`, and `query_length` are log1p-compressed before min-max "
        "scaling, so a few very long real tracking URLs do not stretch the scale for everyone.",
        "",
        f"Each label gets its own certain-distance threshold, calibrated by {DEFAULT_FOLDS}-fold stratified "
        "cross-validation on the training split, so one close call for one label cannot collapse another "
        "label's threshold. The thresholds are intentionally conservative, so uncertain cases fall through to "
        f"local knowledge matching or remote fallback. The calibrated values are {thresholds_text}.",
    ]

    if fold_evaluations:
        average_accuracy = sum(fold.accuracy for fold in fold_evaluations) / len(fold_evaluations)
        average_macro_f1 = sum(fold.macro_f1 for fold in fold_evaluations) / len(fold_evaluations)
        lines.extend(
            [
                "",
                "## 2. Cross Validation",
                "",
                f"Across {len(fold_evaluations)} folds of the training split, average accuracy is "
                f"{average_accuracy:.2%} and average macro F1 is {average_macro_f1:.2%}:",
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
                    "Pooled over all validation folds, per-label metrics are:",
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

    lines.extend(
        [
            "",
            "## 3. Precision and Recall",
            "",
            "On the holdout test split, actual and predicted label counts are:",
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
            "Per-label precision and recall, with the rule baseline on the same split for comparison:",
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

    lines.extend(_source_section(predictions, sources or {}))

    lines.extend(["", "## 5. Error Buckets", ""])
    if errors:
        buckets = error_buckets(predictions)
        lines.extend(
            [
                f"{len(errors)} of {len(predictions)} test rows are misclassified. They fall into these buckets:",
                "",
                "| Bucket | Count |",
                "| :-- | --: |",
            ]
        )

        for bucket, count in buckets.most_common():
            lines.append(f"| {bucket} | {count} |")

        lines.extend(
            [
                "",
                "Each misclassified row, with its distance to the predicted centroid:",
                "",
                "| Actual | Predicted | Distance | URL |",
                "| :-- | :-- | --: | :-- |",
            ]
        )

        for prediction in errors:
            lines.append(
                f"| {prediction.actual} | {prediction.predicted} | {prediction.distance:.4f} | {prediction.url} |"
            )
    else:
        lines.append("No test rows are misclassified.")

    lines.extend(
        [
            "",
            "## 6. Confusion Matrix",
            "",
            "Rows are actual labels and columns are predicted labels:",
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
            "## 7. Predictions",
            "",
            f"All {len(predictions)} test-split predictions, in split order:",
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


def _source_section(predictions: list[ModelPrediction], sources: dict[str, str]) -> list[str]:
    """Holdout metrics per dataset source, plus everything except synthetic.

    Synthetic rows are generated from the same rules the features encode, so
    a pooled score can hide how the model does on real ad URLs.
    """
    groups: dict[str, list[ModelPrediction]] = defaultdict(list)
    for prediction in predictions:
        groups[sources.get(prediction.url, "unknown")].append(prediction)
    non_synthetic = [p for p in predictions if sources.get(p.url) != "synthetic"]

    lines = [
        "",
        "## 4. Holdout by Source",
        "",
        "The pooled holdout mixes synthetic rows with real ad landing URLs. Split by the dataset's "
        "`source` column (per-label F1 with support in parentheses):",
        "",
        "| Source | Rows | Accuracy | Macro F1 | LOW F1 | MEDIUM F1 | HIGH F1 | UNKNOWN F1 |",
        "| :-- | --: | --: | --: | --: | --: | --: | --: |",
    ]
    for name, group in [*sorted(groups.items()), ("non-synthetic (all but synthetic)", non_synthetic)]:
        metrics = precision_recall_by_label(group)
        per_label = " | ".join(
            f"{metrics[label]['f1']:.2%} ({int(metrics[label]['support'])})" for label in LABELS
        )
        lines.append(f"| {name} | {len(group)} | {accuracy(group):.2%} | {macro_f1(group):.2%} | {per_label} |")
    lines.extend(
        [
            "",
            "Macro F1 averages all four labels, so a source with no rows for a label (support 0) "
            "is pulled down by that label's 0% F1; compare per-label F1 where support is non-zero.",
        ]
    )
    return lines


def build_metrics(
    model: dict,
    rows: list[dict[str, str]],
    predictions: list[ModelPrediction],
    baseline_predictions,
    full_baseline_predictions,
    fold_evaluations: list[FoldEvaluation],
    sources: dict[str, str],
) -> dict:
    """Headline numbers exactly as the reports compute them (unrounded)."""
    per_label = precision_recall_by_label(predictions)
    by_source: dict[str, list[ModelPrediction]] = defaultdict(list)
    for prediction in predictions:
        by_source[sources.get(prediction.url, "unknown")].append(prediction)
    by_source["non_synthetic"] = [p for p in predictions if sources.get(p.url) != "synthetic"]

    return {
        "version": model["version"],
        "dataset_sha256": model.get("dataset_sha256"),
        "dataset_rows": len(rows),
        "holdout": {
            "rows": len(predictions),
            "accuracy": accuracy(predictions),
            "macro_f1": macro_f1(predictions),
            "per_label": {
                label: {
                    "precision": per_label[label]["precision"],
                    "recall": per_label[label]["recall"],
                    "f1": per_label[label]["f1"],
                    "support": int(per_label[label]["support"]),
                }
                for label in LABELS
            },
            "error_buckets": dict(error_buckets(predictions)),
        },
        "holdout_by_source": {
            name: {"rows": len(group), "accuracy": accuracy(group)}
            for name, group in sorted(by_source.items())
        },
        "baseline": {
            "holdout_accuracy": baseline_accuracy(baseline_predictions),
            "full_dataset_accuracy": baseline_accuracy(full_baseline_predictions),
        },
        "cv": {
            "folds": len(fold_evaluations),
            "accuracy": sum(fold.accuracy for fold in fold_evaluations) / len(fold_evaluations),
            "macro_f1": sum(fold.macro_f1 for fold in fold_evaluations) / len(fold_evaluations),
        },
    }


def save_metrics(metrics: dict, path: Path = DEFAULT_METRICS) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(metrics, indent=2, sort_keys=True) + "\n")


def dataset_sha256(path: Path = DEFAULT_DATASET) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save_model(model: dict, path: Path = DEFAULT_ARTIFACT) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model, indent=2) + "\n")


def save_report(report: str, path: Path = DEFAULT_REPORT) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(report)


def save_rows(rows: list[dict[str, str]], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("")
        return

    with path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


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


def _error_bucket(prediction: ModelPrediction) -> str:
    url = prediction.url.lower()
    transition = f"{prediction.actual} -> {prediction.predicted}"

    if prediction.actual == "LOW" and prediction.predicted in {"MEDIUM", "HIGH", "UNKNOWN"}:
        if _looks_like_tracking_or_ad_url(url):
            return "LOW tracking/ad URL over-risked"
        return "LOW benign URL lacks enough local context"

    if prediction.actual == "MEDIUM" and prediction.predicted == "LOW":
        return "MEDIUM under-risked by URL-only signal"

    if prediction.actual == "MEDIUM" and prediction.predicted in {"HIGH", "UNKNOWN"}:
        if _looks_like_tracking_or_ad_url(url):
            return "MEDIUM redirect/ad context needs page evidence"
        return "MEDIUM needs page or interaction context"

    if prediction.actual == "HIGH" and prediction.predicted in {"LOW", "MEDIUM", "UNKNOWN"}:
        if _looks_like_brand_or_credential_url(url):
            return "HIGH impersonation signal below threshold"
        return "HIGH malicious signal too weak in URL text"

    if prediction.actual == "UNKNOWN" and prediction.predicted != "UNKNOWN":
        return "UNKNOWN over-classified by sparse URL signal"

    return transition


def _looks_like_tracking_or_ad_url(url: str) -> bool:
    markers = (
        "utm_",
        "gclid",
        "fbclid",
        "adurl=",
        "redirect=",
        "landing_url=",
        "doubleclick",
        "ads.",
        "ad.",
        "click.",
    )
    return any(marker in url for marker in markers)


def _looks_like_brand_or_credential_url(url: str) -> bool:
    markers = (
        "bank",
        "login",
        "verify",
        "account",
        "kakao",
        "naver",
        "kb",
        "shinhan",
        "woori",
        "nh",
    )
    return any(marker in url for marker in markers)


def main() -> None:
    rows = load_rows()
    train_rows, test_rows = stratified_split(rows)
    save_rows(train_rows, DEFAULT_TRAIN_SPLIT)
    save_rows(test_rows, DEFAULT_TEST_SPLIT)
    fold_evaluations, cross_validation_predictions = cross_validate_model(train_rows)
    certain_distance_thresholds = calibrate_certain_distance_thresholds(cross_validation_predictions)
    model = train_centroid_model(train_rows, certain_distance_thresholds=certain_distance_thresholds)
    model["dataset_sha256"] = dataset_sha256()
    predictions = evaluate_model(model, test_rows)
    baseline_predictions = evaluate_baseline(test_rows)
    sources = {row["url"]: row.get("source", "unknown") for row in rows}
    report = render_report(
        train_rows,
        test_rows,
        predictions,
        baseline_predictions,
        fold_evaluations=fold_evaluations,
        cross_validation_predictions=cross_validation_predictions,
        certain_distance_thresholds=certain_distance_thresholds,
        sources=sources,
    )
    metrics = build_metrics(
        model,
        rows,
        predictions,
        baseline_predictions,
        evaluate_baseline(rows),
        fold_evaluations,
        sources,
    )

    save_model(model)
    save_report(report)
    save_metrics(metrics)
    print(report)


if __name__ == "__main__":
    main()
