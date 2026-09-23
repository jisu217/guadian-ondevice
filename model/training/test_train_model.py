import unittest

from train_model import (
    accuracy,
    confusion_matrix,
    evaluate_model,
    load_rows,
    precision_recall_by_label,
    predict,
    render_report,
    stratified_split,
    train_centroid_model,
)


class TrainModelTest(unittest.TestCase):
    def test_stratified_split_keeps_all_labels_in_test_set(self):
        train_rows, test_rows = stratified_split(load_rows())

        self.assertGreater(len(train_rows), len(test_rows))
        self.assertEqual({row["label"] for row in test_rows}, {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})

    def test_trains_centroid_model(self):
        train_rows, _ = stratified_split(load_rows())
        model = train_centroid_model(train_rows)

        self.assertEqual(model["type"], "nearest-centroid")
        self.assertEqual(set(model["centroids"]), {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})

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
        self.assertEqual(set(matrix), {"LOW", "MEDIUM", "HIGH", "UNKNOWN"})
        self.assertEqual(sum(sum(row.values()) for row in matrix.values()), len(predictions))

    def test_report_includes_precision_recall_and_confusion_matrix(self):
        train_rows, test_rows = stratified_split(load_rows())
        model = train_centroid_model(train_rows)
        predictions = evaluate_model(model, test_rows)

        report = render_report(train_rows, test_rows, predictions, predictions)

        self.assertIn("## Precision And Recall", report)
        self.assertIn("## Confusion Matrix", report)


if __name__ == "__main__":
    unittest.main()
