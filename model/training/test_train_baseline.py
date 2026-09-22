import unittest

from train_baseline import accuracy, evaluate, load_rows, predict_label


class TrainBaselineTest(unittest.TestCase):
    def test_predicts_official_domain_as_low(self):
        label, score = predict_label("https://support.example.com/help")

        self.assertEqual(label, "LOW")
        self.assertEqual(score, 0)

    def test_predicts_apk_download_as_high(self):
        label, score = predict_label("https://unknown-site.test/download/app.apk")

        self.assertEqual(label, "HIGH")
        self.assertGreaterEqual(score, 4)

    def test_evaluates_sample_dataset(self):
        predictions = evaluate(load_rows())

        self.assertGreater(len(predictions), 0)
        self.assertGreaterEqual(accuracy(predictions), 0.5)


if __name__ == "__main__":
    unittest.main()
