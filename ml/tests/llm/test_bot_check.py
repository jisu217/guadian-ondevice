import unittest

from guadian_ml.llm.contract.prompt import PageObservation
from guadian_ml.llm.dataset.bot_check import bot_check


def page(status, title, text=""):
    return PageObservation(url="https://shop.example.com/", loaded=True, status=status, title=title, text=text)


class BotCheckTest(unittest.TestCase):
    def test_akamai_denial_is_a_bot_check(self):
        obs = page(403, "Access Denied", "Access Denied You don't have permission to access \"http://shop/\"")
        self.assertEqual(bot_check(obs), "akamai")

    def test_cloudflare_challenge_is_a_bot_check(self):
        obs = page(403, "잠시만 기다리십시오…", "shop.example.com 보안 확인 수행 중")
        self.assertEqual(bot_check(obs), "cloudflare_challenge")

    def test_fake_captcha_served_with_200_stays(self):
        self.assertIsNone(bot_check(page(200, "Security Check", "Verify you are human")))

    def test_cloudflare_phishing_warning_stays(self):
        self.assertIsNone(bot_check(page(403, "Suspected Phishing | Cloudflare", "Warning: Suspected Phishing")))

    def test_plain_error_stays(self):
        self.assertIsNone(bot_check(page(403, "403 Forbidden", "403 Forbidden nginx")))


if __name__ == "__main__":
    unittest.main()
