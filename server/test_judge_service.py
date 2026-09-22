import unittest

from judge_service import JudgeService, Risk, extract_json, parse_verdict_json


class JudgeServiceTest(unittest.TestCase):
    def test_local_precheck_detects_apk(self):
        verdict = JudgeService(api_key=None).judge("https://example.com/app.apk")

        self.assertEqual(Risk.HIGH, verdict.risk)
        self.assertEqual("apk", verdict.type)

    def test_missing_api_key_returns_unknown(self):
        verdict = JudgeService(api_key=None).judge("https://example.org/about")

        self.assertEqual(Risk.UNKNOWN, verdict.risk)
        self.assertEqual("remote_not_configured", verdict.type)

    def test_parse_verdict_json(self):
        verdict = parse_verdict_json(
            '{"risk":"MEDIUM","reason":"Suspicious form.","advice":"Confirm first.","type":"personal_info"}'
        )

        self.assertEqual(Risk.MEDIUM, verdict.risk)
        self.assertEqual("personal_info", verdict.type)

    def test_extract_json_from_wrapped_text(self):
        self.assertEqual('{"risk":"LOW"}', extract_json('Result:\n{"risk":"LOW"}\n'))


if __name__ == "__main__":
    unittest.main()
