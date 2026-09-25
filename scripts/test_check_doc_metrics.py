import unittest

from check_doc_metrics import check_doc, expected_rows, local_resolution_rows

METRICS = {
    "version": "v9",
    "holdout": {
        "accuracy": 0.75,
        "macro_f1": 0.7463,
        "per_label": {
            label: {"precision": 0.5, "recall": 0.25, "f1": 1 / 3, "support": 10}
            for label in ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]
        },
        "error_buckets": {"LOW tracking/ad URL over-risked": 5},
    },
    "holdout_by_source": {"synthetic": {"accuracy": 0.85}, "non_synthetic": {"accuracy": 0.6667}},
    "baseline": {"holdout_accuracy": 0.6439},
    "cv": {"accuracy": 0.8044, "macro_f1": 0.8144},
}

REPORT = """## 2. Holdout Split

| Stage | Resolved | Correct | Correct / resolved |
| :-- | --: | --: | --: |
| Deterministic rules | 29 | 17 | 58.62% |
| On-device model | 9 | 7 | 77.78% |

## 3. Full Dataset

| Stage | Resolved | Correct | Correct / resolved |
| :-- | --: | --: | --: |
| Deterministic rules | 135 | 98 | 72.59% |
"""


class CheckDocMetricsTest(unittest.TestCase):
    def setUp(self):
        self.expected = expected_rows(METRICS, local_resolution_rows(REPORT))

    def test_matching_tables_pass(self):
        doc = """
| Metric | Value |
| :-- | --: |
| Holdout accuracy | 75.00% |
| Rule baseline accuracy (same split) | 64.39% |

| Label | Precision | Recall | F1 | Note |
| :-- | --: | --: | --: | :-- |
| `HIGH` | 50.00% | 25.00% | 33.33% | extra columns are ignored |

| `LOW` tracking/ad URL over-risked | 5 |
"""
        checked, problems = check_doc(doc, self.expected)

        self.assertEqual(4, checked)
        self.assertEqual([], problems)

    def test_stale_number_is_reported(self):
        checked, problems = check_doc("| Holdout accuracy | 79.55% |", self.expected)

        self.assertEqual(1, checked)
        self.assertIn("expected ['75.00%'], found ['79.55%']", problems[0])

    def test_only_the_current_version_row_is_checked(self):
        doc = """
| v8 | 1% | 2% | 3% | 4% | 5% | 6% | 7% |
| v9 | 75.00% | 74.63% | 33.33% | 85.00% | 66.67% | 80.44% | 81.44% |
"""
        checked, problems = check_doc(doc, self.expected)

        self.assertEqual(1, checked)
        self.assertEqual([], problems)

    def test_local_resolution_uses_the_holdout_section_and_sums_a_total(self):
        doc = """
| Deterministic rules | 29 | 17 |
| On-device model | 9 | 7 |
| **Total local** | **38** | **24** |
"""
        checked, problems = check_doc(doc, self.expected)

        self.assertEqual(3, checked)
        self.assertEqual([], problems)

    def test_unrelated_rows_are_ignored(self):
        checked, problems = check_doc("| Something else | 12% |", self.expected)

        self.assertEqual(0, checked)
        self.assertEqual([], problems)


if __name__ == "__main__":
    unittest.main()
