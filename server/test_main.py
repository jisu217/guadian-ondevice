import unittest

from fastapi.testclient import TestClient

import main


class RemoteJudgeApiTest(unittest.TestCase):
    def setUp(self):
        main.service.api_key = None
        self.client = TestClient(main.app)

    def test_health(self):
        response = self.client.get("/health")

        self.assertEqual(200, response.status_code)
        self.assertEqual({"status": "ok"}, response.json())

    def test_judge_without_api_key_returns_unknown(self):
        response = self.client.post("/judge", json={"url": "https://example.org/about"})

        self.assertEqual(200, response.status_code)
        self.assertEqual("UNKNOWN", response.json()["risk"])
        self.assertEqual("remote_not_configured", response.json()["type"])

    def test_judge_detects_apk_locally(self):
        response = self.client.post("/judge", json={"url": "https://example.com/app.apk"})

        self.assertEqual(200, response.status_code)
        self.assertIn("application/json", response.headers["content-type"])
        self.assertEqual("HIGH", response.json()["risk"])
        self.assertEqual("apk", response.json()["type"])


if __name__ == "__main__":
    unittest.main()
