import unittest

from guadian_ml.llm.evaluation.agreement import agreement, risk_reader


class AgreementTest(unittest.TestCase):
    def test_compares_only_cases_every_file_answered(self):
        record = agreement({
            "f16": {"a": "LOW", "b": "HIGH", "c": "MEDIUM"},
            "q5_k_m": {"a": "LOW", "b": "MEDIUM", "c": "MEDIUM"},
            "q4_k_m": {"a": "LOW", "b": "HIGH"},
        })
        self.assertEqual(record["cases"], 2)
        self.assertEqual(record["all_agree"], 1 / 2)
        self.assertEqual(record["pairwise"]["f16|q4_k_m"], 1.0)

    def test_unparseable_reply_reads_as_medium(self):
        read = risk_reader()
        self.assertEqual(read("no json here"), "MEDIUM")
        self.assertEqual(read('{"risk": "HIGH", "type": "apk", "evidence": ""}'), "HIGH")


if __name__ == "__main__":
    unittest.main()
