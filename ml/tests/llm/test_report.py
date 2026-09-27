import unittest

from guadian_ml.llm.evaluation import report


class ReportTest(unittest.TestCase):
    def test_committed_report_matches_the_records(self):
        self.assertEqual(report.OUT.read_text(encoding="utf-8"), report.render(),
                         "run python -m guadian_ml.llm.evaluation.report")


if __name__ == "__main__":
    unittest.main()
