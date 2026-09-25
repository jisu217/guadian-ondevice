import unittest

from train_model import (
    MODEL_VERSION,
    accuracy,
    calibrate_certain_distance_thresholds,
    confusion_matrix,
    cross_validate_model,
    evaluate_model,
    error_buckets,
    load_rows,
    macro_f1,
    precision_recall_by_label,
    predict,
    render_report,
    save_rows,
    stratified_kfold_split,
    stratified_split,
    train_centroid_model,
)


class TrainModelTest(unittest.TestCase):
    def test_stratified_split_keeps_all_labels_in_test_set(self):
        train_rows, test_rows = stratified_split(load_rows())

        self.assertGreater(len(train_rows), len(test_rows))
        self.assertEqual({row["label"] for row in test_rows}, {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})

    def test_stratified_kfold_split_keeps_each_fold_label_aware(self):
        train_rows, _ = stratified_split(load_rows())
        splits = stratified_kfold_split(train_rows)

        self.assertEqual(len(splits), 5)
        for _, validation_rows in splits:
            self.assertEqual({row["label"] for row in validation_rows}, {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})

    def test_trains_centroid_model(self):
        train_rows, _ = stratified_split(load_rows())
        model = train_centroid_model(train_rows)

        self.assertEqual(model["type"], "nearest-centroid")
        self.assertEqual(set(model["centroids"]), {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})
        self.assertIn("certain_distance_thresholds", model)

    def test_predicts_known_url_label(self):
        train_rows, _ = stratified_split(load_rows())
        model = train_centroid_model(train_rows)

        label, distance = predict(model, "https://unknown-site.test/download/app.apk")

        self.assertIn(label, {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})
        self.assertGreaterEqual(distance, 0.0)

    def test_evaluates_model_on_test_split(self):
        train_rows, test_rows = stratified_split(load_rows())
        model = train_centroid_model(train_rows)
        predictions = evaluate_model(model, test_rows)

        self.assertEqual(len(predictions), len(test_rows))
        self.assertGreaterEqual(accuracy(predictions), 0.25)

    def test_computes_precision_recall_and_confusion_matrix(self):
        train_rows, test_rows = stratified_split(load_rows())
        model = train_centroid_model(train_rows)
        predictions = evaluate_model(model, test_rows)

        metrics = precision_recall_by_label(predictions)
        matrix = confusion_matrix(predictions)

        self.assertEqual(set(metrics), {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})
        self.assertGreaterEqual(macro_f1(predictions), 0.0)
        self.assertEqual(set(matrix), {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})
        self.assertEqual(sum(sum(row.values()) for row in matrix.values()), len(predictions))

    def test_cross_validates_and_calibrates_thresholds(self):
        train_rows, _ = stratified_split(load_rows())

        folds, predictions = cross_validate_model(train_rows)
        thresholds = calibrate_certain_distance_thresholds(predictions)

        self.assertEqual(len(folds), 5)
        self.assertEqual(len(predictions), len(train_rows))
        self.assertEqual(set(thresholds), {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})
        for threshold in thresholds.values():
            self.assertGreaterEqual(threshold, 0.0)

    def test_report_includes_precision_recall_and_confusion_matrix(self):
        train_rows, test_rows = stratified_split(load_rows())
        model = train_centroid_model(train_rows)
        predictions = evaluate_model(model, test_rows)

        report = render_report(train_rows, test_rows, predictions, predictions)

        self.assertIn("## 1. Validation Protocol", report)
        self.assertIn("model/data/test-split.csv", report)
        self.assertIn("## 3. Precision and Recall", report)
        self.assertIn("macro F1", report)
        self.assertIn("## 5. Error Buckets", report)
        self.assertIn("## 6. Confusion Matrix", report)

    def test_report_splits_holdout_by_source(self):
        train_rows, test_rows = stratified_split(load_rows())
        model = train_centroid_model(train_rows)
        predictions = evaluate_model(model, test_rows)
        sources = {row["url"]: row["source"] for row in test_rows}

        report = render_report(train_rows, test_rows, predictions, predictions, sources=sources)

        self.assertIn("## 4. Holdout by Source", report)
        self.assertIn("| synthetic |", report)
        self.assertIn("| non-synthetic (all but synthetic) |", report)

    def test_trained_artifact_records_version(self):
        model = train_centroid_model(load_rows()[:40])

        self.assertEqual(model["version"], MODEL_VERSION)

    def test_groups_holdout_errors_into_actionable_buckets(self):
        predictions = [
            self._prediction("https://ad.example.com/click?utm_source=ad", "LOW", "MEDIUM"),
            self._prediction("https://bank-login-verification.test/account", "HIGH", "MEDIUM"),
        ]

        buckets = error_buckets(predictions)

        self.assertEqual(buckets["LOW tracking/ad URL over-risked"], 1)
        self.assertEqual(buckets["HIGH impersonation signal below threshold"], 1)

    def test_saves_evaluation_split_rows(self):
        rows = load_rows()[:3]
        path = self._tmp_path("split.csv")

        save_rows(rows, path)

        saved = path.read_text()
        self.assertIn("url,label,source,reason,category,brand,reviewed_by,created_at", saved)
        self.assertEqual(len(saved.strip().splitlines()), 4)

    def _tmp_path(self, name):
        import tempfile
        from pathlib import Path

        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        return Path(directory.name) / name

    def _prediction(self, url, actual, predicted):
        from train_model import ModelPrediction

        return ModelPrediction(url=url, actual=actual, predicted=predicted, distance=0.5)


if __name__ == "__main__":
    unittest.main()
