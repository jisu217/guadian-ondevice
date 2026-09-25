import csv
import unittest
from pathlib import Path

from judge_service import JudgeService, Risk, Status, extract_json, parse_verdict_json, precheck_category

RULE_PARITY_FIXTURE = Path(__file__).parents[1] / "model" / "data" / "fixtures" / "rule_parity_cases.csv"


class JudgeServiceTest(unittest.TestCase):
    def test_local_precheck_detects_apk(self):
        verdict = JudgeService(api_key=None).judge("https://example.com/app.apk")

        self.assertEqual(Risk.HIGH, verdict.risk)
        self.assertEqual("apk", verdict.category)

    def test_local_precheck_matches_whole_suspicious_token(self):
        verdict = JudgeService(api_key=None).judge("https://promo.example.com/free-gift")

        self.assertEqual(Risk.MEDIUM, verdict.risk)
        self.assertEqual("suspicious_url", verdict.category)

    def test_local_precheck_ignores_suspicious_substring(self):
        verdict = JudgeService(api_key=None).judge("https://prevent.example.org/freedom")

        self.assertEqual(Risk.UNKNOWN, verdict.risk)
        self.assertEqual(Status.NOT_CONFIGURED, verdict.status)

    def test_missing_api_key_returns_unknown(self):
        verdict = JudgeService(api_key=None).judge("https://example.org/about")

        self.assertEqual(Risk.UNKNOWN, verdict.risk)
        self.assertEqual(Status.NOT_CONFIGURED, verdict.status)

    def test_parse_verdict_json(self):
        verdict = parse_verdict_json(
            '{"risk":"MEDIUM","reason":"Suspicious form.","advice":"Confirm first.","category":"personal_info","host":"form.example"}'
        )

        self.assertEqual(Risk.MEDIUM, verdict.risk)
        self.assertEqual("personal_info", verdict.category)
        self.assertEqual("form.example", verdict.host)
        self.assertEqual(Status.OK, verdict.status)

    def test_precheck_ignores_suspicious_host(self):
        self.assertEqual("none", precheck_category("https://account.example.com/"))

    def test_precheck_matches_rule_parity_fixture(self):
        # The same fixture is checked by the app's RuleParityTest.
        with RULE_PARITY_FIXTURE.open(newline="") as handle:
            rows = list(csv.DictReader(handle))

        self.assertGreater(len(rows), 0)
        for row in rows:
            self.assertEqual(row["category"], precheck_category(row["url"]), row["url"])

    def test_extract_json_from_wrapped_text(self):
        self.assertEqual('{"risk":"LOW"}', extract_json('Result:\n{"risk":"LOW"}\n'))


if __name__ == "__main__":
    unittest.main()
