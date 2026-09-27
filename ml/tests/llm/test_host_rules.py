import unittest

from guadian_ml.llm.contract.host_rules import grade_host


class GradeHostTest(unittest.TestCase):
    def assertGrade(self, url, expected):
        verdict = grade_host(url)
        self.assertEqual(verdict.risk if verdict else None, expected, verdict)

    def test_embedded_official_domain_is_high(self):
        self.assertGrade("https://roblox.com.mu/games/1", "HIGH")
        self.assertGrade("https://https-wvvw-roblox.com/users/1", "HIGH")

    def test_brand_with_account_word_is_medium(self):
        self.assertGrade("http://www.xfinityuserverification.weebly.com/", "MEDIUM")
        self.assertGrade("https://kb-star-login.top/", "MEDIUM")

    def test_brand_name_alone_decides_nothing(self):
        self.assertGrade("https://www.meritzevent.com/", None)
        self.assertGrade("https://kb-star.top/", None)
        self.assertGrade("http://microsoftsteam.live/", None)   # would need the domain's age
        self.assertGrade("https://netflix-clone-red-three.vercel.app/", None)

    def test_official_domains_are_not_graded(self):
        self.assertGrade("https://m.kbstar.com/login", None)
        self.assertGrade("https://www.roblox.com/games/1", None)
        self.assertGrade("https://login.microsoftonline.com/", None)

    def test_unrelated_hosts_are_not_graded(self):
        self.assertGrade("https://m.fmkorea.com/", None)
        self.assertGrade("https://delekoomda.com/", None)

    def test_embedded_domain_needs_a_label_boundary(self):
        self.assertGrade("https://olive.com/", None)
        self.assertGrade("https://kb-star.com.evil.top/", "HIGH")


if __name__ == "__main__":
    unittest.main()
