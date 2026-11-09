import unittest

from guadian_ml.llm.evaluation.agreement import agreement, answer_reader


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

    def test_reads_risk_and_question_replies(self):
        read = answer_reader()
        self.assertEqual(read("no json here"), "MEDIUM")
        self.assertEqual(read('{"risk": "HIGH", "type": "apk", "evidence": ""}'), "HIGH")
        quiet = ('"sign_in": false, "payment": false, "investment": false, "reward_pressure": false, '
                 '"content_farm": false')
        a = read('{"claims": "KB", "evidence": "제목: KB", ' + quiet + '}')
        b = read('{"claims": "KB국민은행", "evidence": "제목: KB", ' + quiet + '}')
        c = read('{"claims": null, "evidence": "", ' + quiet + '}')
        self.assertEqual(a, b)     # the claimed name may differ; whether a claim was made is compared
        self.assertNotEqual(a, c)


if __name__ == "__main__":
    unittest.main()
